package de.jpx3.intave.check.combat.heuristics.modern;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.ConnectionMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;

import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.TELEPORT;
import static de.jpx3.intave.module.linker.packet.ListenerPriority.LOW;
import static de.jpx3.intave.module.linker.packet.ListenerPriority.NORMAL;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.ATTACK_ENTITY;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.FLYING;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.LOOK;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION_LOOK;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.USE_ENTITY;

/**
 * Detects attack bursts delivered while regular movement packets are being withheld. The score is
 * deliberately buffered so one ordinary lag spike does not become a combat violation.
 */
public final class CombatFreezeHeuristic extends ModernCombatHeuristic<CombatFreezeHeuristic.Meta> {
  private static final long GAP_THRESHOLD_MS = 1500L;
  private static final long STRONG_GAP_THRESHOLD_MS = 2000L;
  private static final int SCORE_CANCEL = 3;
  private static final int SCORE_FLAG = 5;

  public CombatFreezeHeuristic(Heuristics parentCheck) {
    super(parentCheck, Meta.class);
  }

  @PacketSubscription(priority = LOW, packetsIn = {ATTACK_ENTITY, USE_ENTITY}, ignoreCancelled = false)
  public void receiveAttackPacket(ProtocolPacketEvent event) {
    EntityUseReader reader = PacketReaders.readerOf(event.getPacket());
    try {
      if (!reader.isAttackPacket()) {
        return;
      }

      User user = userOf(event.getPlayer());
      MovementMetadata movement = user.meta().movement();
      if (movement.ticksPast(TELEPORT) <= 3) {
        return;
      }

      ConnectionMetadata connection = user.meta().connection();
      long lastMovement = connection.lastMovementPacket();
      if (lastMovement <= 0L) {
        return;
      }

      long gap = System.currentTimeMillis() - lastMovement;
      Meta meta = metaOf(user);
      if (gap < GAP_THRESHOLD_MS || gap > 10_000L) {
        meta.score = Math.max(0, meta.score - 1);
        meta.attacksDuringGap = 0;
        return;
      }

      meta.attacksDuringGap++;
      meta.lastGap = gap;
      meta.score = Math.min(10, meta.score + (gap >= STRONG_GAP_THRESHOLD_MS ? 2 : 1));

      if (meta.score >= SCORE_CANCEL) {
        if (event.isReadOnly()) {
          event.setReadOnly(false);
        }
        event.setCancelled(true);
      }

      if (meta.score >= SCORE_FLAG && meta.attacksDuringGap >= 2
        && System.currentTimeMillis() - meta.lastFlag > 500L) {
        flag(user, "freeze-attack",
          "gap=" + gap + "ms attacks=" + meta.attacksDuringGap + " score=" + meta.score,
          4.0);
        meta.lastFlag = System.currentTimeMillis();
        meta.score = Math.max(SCORE_CANCEL, meta.score - 2);
      }
    } finally {
      reader.release();
    }
  }

  @PacketSubscription(priority = NORMAL, packetsIn = {FLYING, LOOK, POSITION, POSITION_LOOK})
  public void receiveMovementPacket(ProtocolPacketEvent event) {
    Meta meta = metaOf(userOf(event.getPlayer()));
    if (meta.score > 0) {
      meta.score--;
    }
    if (meta.score == 0) {
      meta.attacksDuringGap = 0;
      meta.lastGap = 0L;
    }
  }

  public static final class Meta extends CheckCustomMetadata {
    private int score;
    private int attacksDuringGap;
    private long lastGap;
    private long lastFlag;
  }
}

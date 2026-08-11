package de.jpx3.intave.check.combat.clickpatterns;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.ClickPatterns;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.module.tracker.entity.EntityTracker;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.PacketTypes;
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;

import static de.jpx3.intave.module.linker.packet.ListenerPriority.HIGH;
import static de.jpx3.intave.module.linker.packet.ListenerPriority.NORMAL;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

/**
 * Attack-packet timing analysis for modern clients. Legacy ClickPatterns intentionally ignores
 * 1.13+ swing packets because vanilla/client-side click behaviour changed; this detector only
 * samples confirmed attacks against players and therefore does not inherit that false-positive
 * source.
 */
public final class ModernAttackIntervals extends MetaCheckPart<ClickPatterns, ModernAttackIntervals.Meta> {
  private static final int WINDOW = 10;
  private static final long MAX_SPREAD_MS = 50L;

  public ModernAttackIntervals(ClickPatterns parentCheck) {
    super(parentCheck, Meta.class);
  }

  @PacketSubscription(priority = HIGH, packetsIn = {ATTACK_ENTITY, USE_ENTITY}, ignoreCancelled = false)
  public void attack(PacketEvent event) {
    EntityUseReader reader = PacketReaders.readerOf(event.getPacket());
    try {
      if (!reader.isAttackPacket()) {
        return;
      }

      User user = userOf(event.getPlayer());
      MovementMetadata movement = user.meta().movement();
      Meta meta = metaOf(user);
      if (movement.recordedMoves < 40
        || movement.awaitTeleport || movement.expectTeleport
        || movement.isInVehicle()
        || user.meta().abilities().ignoringMovementPackets()) {
        resetSamples(meta);
        return;
      }

      Entity target = EntityTracker.entityByIdentifier(user, reader.entityId());
      if (target == null || !target.isPlayer || !target.isEntityAlive()) {
        return;
      }

      long now = System.currentTimeMillis();
      if (meta.ticksSinceAttack <= 3) {
        // Do not turn high-CPS combat into an interval-pattern check; ClickSpeedLimiter owns rate.
        meta.ticksSinceAttack = 0;
        meta.lastAttackMs = now;
        meta.sampleCount = 0;
        return;
      }
      meta.ticksSinceAttack = 0;

      if (meta.lastAttackMs == 0L) {
        meta.lastAttackMs = now;
        return;
      }

      long interval = now - meta.lastAttackMs;
      meta.lastAttackMs = now;
      if (interval < 1L || interval > 2_000L) {
        meta.sampleCount = 0;
        return;
      }

      meta.intervals[meta.sampleCount++] = interval;
      if (meta.sampleCount < WINDOW) {
        return;
      }

      long min = meta.intervals[0];
      long max = meta.intervals[0];
      for (int i = 1; i < WINDOW; i++) {
        min = Math.min(min, meta.intervals[i]);
        max = Math.max(max, meta.intervals[i]);
      }
      long spread = max - min;
      meta.sampleCount = 0;

      if (spread < MAX_SPREAD_MS && tickingReliably(user)) {
        meta.buffer += 1.0D;
        if (meta.buffer > 4.0D) {
          Violation violation = Violation.builderFor(ClickPatterns.class)
            .forPlayer(user.player())
            .withCheckName("AutoClicker")
            .withMessage("attacked with inhuman interval consistency")
            .withDetails("spread=" + spread + "ms, min=" + min + "ms, max=" + max + "ms")
            .withVL(2.0D)
            .build();
          Modules.violationProcessor().processViolation(violation);
          meta.buffer = 2.0D;
        }
      } else {
        meta.buffer = Math.max(0.0D, meta.buffer - 0.25D);
      }
    } finally {
      reader.release();
    }
  }

  @PacketSubscription(
    priority = NORMAL,
    packetsIn = {FLYING, LOOK, POSITION, POSITION_LOOK, CLIENT_TICK_END},
    ignoreCancelled = false
  )
  public void tick(PacketEvent event) {
    User user = userOf(event.getPlayer());
    PacketType type = event.getPacketType();
    boolean endTick = PacketTypes.isClientEndTick(type);
    if (user.meta().protocol().sendsClientTickEnd()) {
      if (!endTick) return;
    } else if (endTick) {
      return;
    }

    Meta meta = metaOf(user);
    if (meta.ticksSinceAttack < Integer.MAX_VALUE) {
      meta.ticksSinceAttack++;
    }
    meta.buffer = Math.max(0.0D, meta.buffer - 0.0025D);
  }

  private static boolean tickingReliably(User user) {
    double average = user.meta().connection().averageMovementPacketTimestamp();
    return average > 0.0D && average <= 90.0D;
  }

  private static void resetSamples(Meta meta) {
    meta.sampleCount = 0;
    meta.lastAttackMs = 0L;
    meta.ticksSinceAttack = Integer.MAX_VALUE;
    meta.buffer = Math.max(0.0D, meta.buffer - 0.5D);
  }

  public static final class Meta extends CheckCustomMetadata {
    private final long[] intervals = new long[WINDOW];
    private int sampleCount;
    private long lastAttackMs;
    private int ticksSinceAttack = Integer.MAX_VALUE;
    private double buffer;
  }
}

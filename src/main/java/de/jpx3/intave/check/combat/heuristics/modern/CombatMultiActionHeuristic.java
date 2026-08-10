package de.jpx3.intave.check.combat.heuristics.modern;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.PacketTypes;
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.InventoryMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;

import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.TELEPORT;
import static de.jpx3.intave.module.linker.packet.ListenerPriority.HIGH;
import static de.jpx3.intave.module.linker.packet.ListenerPriority.NORMAL;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

/**
 * Independent KillAura signals for action combinations that rotation/reach checks cannot see:
 * attacking while a slowing item-use state is active and repeatedly switching attack targets inside
 * one client tick. Both paths are buffered and share the existing Heuristics VL/config pipeline.
 */
public final class CombatMultiActionHeuristic extends ModernCombatHeuristic<CombatMultiActionHeuristic.Meta> {
  public CombatMultiActionHeuristic(Heuristics parentCheck) {
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
      if (movement.ticksPast(TELEPORT) <= 2 || movement.awaitTeleport || movement.expectTeleport
        || movement.isInVehicle() || user.meta().abilities().ignoringMovementPackets()) {
        resetTick(meta);
        meta.itemUseBuffer = Math.max(0.0D, meta.itemUseBuffer - 0.5D);
        meta.multiTargetBuffer = Math.max(0.0D, meta.multiTargetBuffer - 0.5D);
        return;
      }

      InventoryMetadata inventory = user.meta().inventory();
      boolean stableItemUse = inventory.handActive()
        && inventory.handActiveTicks > 0
        && inventory.pastItemUsageTransition > 0;
      if (stableItemUse) {
        meta.itemUseBuffer += 1.0D;
        if (meta.itemUseBuffer >= 2.0D) {
          flag(user, "attack-while-using-item",
            "item=" + inventory.activeItemType()
              + " handTicks=" + inventory.handActiveTicks
              + " target=" + reader.entityId(),
            3.0D);
          meta.itemUseBuffer = 1.0D;
        }
      } else {
        meta.itemUseBuffer = Math.max(0.0D, meta.itemUseBuffer - 0.35D);
      }

      int target = reader.entityId();
      if (meta.attackSeenThisTick && target != meta.lastTargetId && tickingReliably(user)) {
        meta.multiTargetBuffer += 1.0D;
        if (meta.multiTargetBuffer >= 2.0D) {
          flag(user, "multi-target",
            "lastTarget=" + meta.lastTargetId + " target=" + target
              + " attacksThisTick=" + (meta.attacksThisTick + 1),
            4.0D);
          meta.multiTargetBuffer = 1.0D;
        }
      } else if (!meta.attackSeenThisTick) {
        meta.multiTargetBuffer = Math.max(0.0D, meta.multiTargetBuffer - 0.10D);
      }

      meta.attackSeenThisTick = true;
      meta.lastTargetId = target;
      meta.attacksThisTick++;
    } finally {
      reader.release();
    }
  }

  @PacketSubscription(
    priority = NORMAL,
    packetsIn = {FLYING, LOOK, POSITION, POSITION_LOOK, CLIENT_TICK_END},
    ignoreCancelled = false
  )
  public void tickBoundary(PacketEvent event) {
    User user = userOf(event.getPlayer());
    PacketType packetType = event.getPacketType();
    boolean clientTickEnd = PacketTypes.isClientEndTick(packetType);
    if (user.meta().protocol().sendsClientTickEnd()) {
      if (!clientTickEnd) {
        return;
      }
    } else if (clientTickEnd) {
      return;
    }

    resetTick(metaOf(user));
  }

  private static boolean tickingReliably(User user) {
    double average = user.meta().connection().averageMovementPacketTimestamp();
    return average > 0.0D && average <= 90.0D;
  }

  private static void resetTick(Meta meta) {
    meta.attackSeenThisTick = false;
    meta.lastTargetId = Integer.MIN_VALUE;
    meta.attacksThisTick = 0;
  }

  public static final class Meta extends CheckCustomMetadata {
    private boolean attackSeenThisTick;
    private int lastTargetId = Integer.MIN_VALUE;
    private int attacksThisTick;
    private double itemUseBuffer;
    private double multiTargetBuffer;
  }
}

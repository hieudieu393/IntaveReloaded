package de.jpx3.intave.check.combat.heuristics.modern;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.PacketTypes;
import de.jpx3.intave.packet.reader.BlockDigReader;
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.InventoryMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;

import java.util.Locale;

import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.TELEPORT;
import static de.jpx3.intave.module.linker.packet.ListenerPriority.HIGH;
import static de.jpx3.intave.module.linker.packet.ListenerPriority.NORMAL;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

/**
 * Independent KillAura signals for action combinations that rotation/reach checks cannot see:
 * attacking or swinging while a slowing item-use state is active and repeatedly switching attack
 * targets inside one client tick. Each path has its own buffer and shares the existing Heuristics
 * VL/config pipeline.
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
      if (hardExempt(user, movement)) {
        resetTick(meta);
        meta.itemUseBuffer = Math.max(0.0D, meta.itemUseBuffer - 0.5D);
        meta.multiTargetBuffer = Math.max(0.0D, meta.multiTargetBuffer - 0.5D);
        return;
      }

      InventoryMetadata inventory = user.meta().inventory();
      boolean stableItemUse = stableItemUse(inventory);
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

  @PacketSubscription(priority = HIGH, packetsIn = BLOCK_DIG, ignoreCancelled = false)
  public void dig(PacketEvent event) {
    User user = userOf(event.getPlayer());
    Meta meta = metaOf(user);
    BlockDigReader reader = PacketReaders.readerOf(event.getPacket());
    try {
      EnumWrappers.PlayerDigType action = reader.action();
      String actionName = action == null ? "" : action.name().toUpperCase(Locale.ROOT);
      if (actionName.contains("DROP")) {
        meta.droppingThisTick = true;
      }
    } finally {
      reader.release();
    }
  }

  @PacketSubscription(priority = HIGH, packetsIn = ARM_ANIMATION, ignoreCancelled = false)
  public void swing(PacketEvent event) {
    User user = userOf(event.getPlayer());
    MovementMetadata movement = user.meta().movement();
    Meta meta = metaOf(user);
    if (meta.droppingThisTick || hardExempt(user, movement)) {
      meta.swingUseBuffer = Math.max(0.0D, meta.swingUseBuffer - 0.25D);
      return;
    }

    InventoryMetadata inventory = user.meta().inventory();
    if (!stableItemUse(inventory)) {
      meta.swingUseBuffer = Math.max(0.0D, meta.swingUseBuffer - 0.25D);
      return;
    }

    meta.swingUseBuffer += 1.0D;
    if (meta.swingUseBuffer >= 2.5D && tickingReliably(user)) {
      flag(user, "swing-while-using-item",
        "item=" + inventory.activeItemType() + " handTicks=" + inventory.handActiveTicks,
        2.0D);
      meta.swingUseBuffer = 1.25D;
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

    Meta meta = metaOf(user);
    resetTick(meta);
    meta.droppingThisTick = false;
  }

  private static boolean stableItemUse(InventoryMetadata inventory) {
    return inventory.handActive()
      && inventory.handActiveTicks > 0
      && inventory.pastItemUsageTransition > 0;
  }

  private static boolean hardExempt(User user, MovementMetadata movement) {
    return movement.ticksPast(TELEPORT) <= 2
      || movement.awaitTeleport
      || movement.expectTeleport
      || movement.isInVehicle()
      || user.meta().abilities().ignoringMovementPackets();
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
    private boolean droppingThisTick;
    private double itemUseBuffer;
    private double multiTargetBuffer;
    private double swingUseBuffer;
  }
}

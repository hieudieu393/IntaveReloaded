package de.jpx3.intave.check.world.breakspeedlimiter;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.world.BreakSpeedLimiter;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.InventoryMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;

import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.TELEPORT;
import static de.jpx3.intave.module.linker.packet.ListenerPriority.LOWEST;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.BLOCK_DIG;

/** Layered MultiActions signal: mining while a stable item-use slowdown state is active. */
public final class UseItemBreakCheck extends MetaCheckPart<BreakSpeedLimiter, UseItemBreakCheck.Meta> {
  public UseItemBreakCheck(BreakSpeedLimiter parentCheck) {
    super(parentCheck, Meta.class);
  }

  @PacketSubscription(priority = LOWEST, packetsIn = BLOCK_DIG, ignoreCancelled = false)
  public void receive(ProtocolPacketEvent event) {
    if (!(event.delegate() instanceof PacketReceiveEvent)) {
      return;
    }

    WrapperPlayClientPlayerDigging dig = new WrapperPlayClientPlayerDigging((PacketReceiveEvent) event.delegate());
    DiggingAction action = dig.getAction();
    if (action != DiggingAction.START_DIGGING && action != DiggingAction.FINISHED_DIGGING) {
      return;
    }

    User user = userOf(event.getPlayer());
    MovementMetadata movement = user.meta().movement();
    Meta meta = metaOf(user);
    if (movement.ticksPast(TELEPORT) <= 2 || movement.awaitTeleport || movement.expectTeleport
      || movement.isInVehicle() || user.meta().abilities().ignoringMovementPackets()) {
      meta.buffer = Math.max(0.0D, meta.buffer - 0.5D);
      return;
    }

    InventoryMetadata inventory = user.meta().inventory();
    boolean stableUse = inventory.handActive()
      && inventory.handActiveTicks > 1
      && inventory.pastItemUsageTransition > 1
      && !inventory.activatedItemThisTick
      && !inventory.deactivatedItemThisTick;
    if (!stableUse) {
      meta.buffer = Math.max(0.0D, meta.buffer - 0.35D);
      return;
    }

    meta.buffer += 1.0D;
    if (meta.buffer < 2.0D) {
      return;
    }

    Violation violation = Violation.builderFor(BreakSpeedLimiter.class)
      .forPlayer(user.player())
      .withCheckName("FastBreak")
      .withMessage("broke a block while using an item")
      .withDetails("item=" + inventory.activeItemType()
        + ", handTicks=" + inventory.handActiveTicks
        + ", action=" + action)
      .withVL(3.0D)
      .build();
    Modules.violationProcessor().processViolation(violation);
    meta.buffer = 1.0D;
  }

  public static final class Meta extends CheckCustomMetadata {
    private double buffer;
  }
}

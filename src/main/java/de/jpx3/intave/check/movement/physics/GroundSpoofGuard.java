package de.jpx3.intave.check.movement.physics;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.check.MetaCheck;
import de.jpx3.intave.check.movement.Physics;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.packet.reader.PlayerMoveReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AbilityMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;

import static de.jpx3.intave.module.linker.packet.ListenerPriority.LOWEST;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.tracker.player.AbilityTracker.GameMode.SPECTATOR;

/**
 * A second, packet-state-oriented NoFall signal on top of Physics prediction. It intentionally
 * relies on Physics' already-computed environment (`onGroundNoBlocks`, collision, base motion)
 * instead of running a second collision engine. This gives overlapping coverage without creating
 * a competing movement simulator.
 */
public final class GroundSpoofGuard extends MetaCheck<GroundSpoofGuard.Meta> {
  public GroundSpoofGuard() {
    super("GroundSpoofGuard", "groundspoofguard", Meta.class);
  }

  @Override
  public boolean enabled() {
    return true;
  }

  @Override
  public boolean performLinkage() {
    return true;
  }

  @PacketSubscription(
    priority = LOWEST,
    ignoreCancelled = false,
    packetsIn = {FLYING, LOOK, POSITION, POSITION_LOOK}
  )
  public void receive(ProtocolPacketEvent event) {
    User user = userOf(event.getPlayer());
    MovementMetadata movement = user.meta().movement();
    AbilityMetadata abilities = user.meta().abilities();
    Meta meta = metaOf(user);

    if (exempt(movement, abilities)) {
      meta.buffer = Math.max(0.0, meta.buffer - 0.5);
      return;
    }

    PlayerMoveReader reader = PacketReaders.readerOf(event.getPacket());
    try {
      if (!reader.onGround()) {
        meta.buffer = Math.max(0.0, meta.buffer - 0.15);
        return;
      }

      // `onGroundNoBlocks` is set by the Physics environment when the claimed ground state has no
      // supporting collision. Requiring downward/no-positive motion removes ordinary edge steps.
      boolean descending = movement.baseMotionY < -0.015 || movement.positionY < movement.lastPositionY - 0.005;
      boolean impossibleGround = movement.onGroundNoBlocks
        && !movement.collidedVertically
        && descending;
      if (!impossibleGround) {
        meta.buffer = Math.max(0.0, meta.buffer - 0.35);
        return;
      }

      meta.buffer += movement.baseMotionY < -0.08 ? 1.0 : 0.6;
      if (meta.buffer < 2.0) {
        return;
      }

      Violation violation = Violation.builderFor(Physics.class)
        .forPlayer(user.player())
        .withCheckName("NoFall")
        .withMessage("claimed an impossible ground state")
        .withDetails("dy=" + format(movement.positionY - movement.lastPositionY)
          + ", baseY=" + format(movement.baseMotionY))
        .withVL(2.0)
        .build();
      Modules.violationProcessor().processViolation(violation);
      meta.buffer = Math.max(0.75, meta.buffer - 0.75);
    } finally {
      reader.release();
    }
  }

  private static boolean exempt(MovementMetadata movement, AbilityMetadata abilities) {
    return movement.isInVehicle()
      || movement.awaitTeleport
      || movement.expectTeleport
      || movement.inRespawnScreen
      || movement.gliding
      || movement.inWater
      || movement.inWeb
      || movement.onLadderLast
      || movement.interactingFluid != null
      || movement.pushedByEntity
      || abilities.probablyFlying()
      || abilities.inGameModeIncludePending(SPECTATOR);
  }

  private static String format(double value) {
    return String.format(java.util.Locale.ROOT, "%.4f", value);
  }

  public static final class Meta extends CheckCustomMetadata {
    private double buffer;
  }
}

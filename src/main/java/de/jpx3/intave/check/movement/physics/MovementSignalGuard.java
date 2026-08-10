package de.jpx3.intave.check.movement.physics;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.MetaCheck;
import de.jpx3.intave.check.movement.Physics;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AbilityMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;

import static de.jpx3.intave.module.linker.packet.ListenerPriority.LOWEST;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.tracker.player.AbilityTracker.GameMode.SPECTATOR;

/**
 * Adds independent labels/signals on top of Physics' existing simulator. It deliberately consumes
 * simulation state instead of implementing another movement predictor, so NoSlow/Phase coverage
 * becomes broader while all tolerances remain owned by Physics.
 */
public final class MovementSignalGuard extends MetaCheck<MovementSignalGuard.Meta> {
  public MovementSignalGuard() {
    super("MovementSignalGuard", "movementsignalguard", Meta.class);
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
    packetsIn = {FLYING, LOOK, POSITION, POSITION_LOOK, CLIENT_TICK_END}
  )
  public void receive(PacketEvent event) {
    User user = userOf(event.getPlayer());
    MovementMetadata movement = user.meta().movement();
    Meta meta = metaOf(user);

    if (hardExempt(user, movement)) {
      meta.noSlowBuffer = Math.max(0.0, meta.noSlowBuffer - 0.5);
      meta.phaseBuffer = Math.max(0.0, meta.phaseBuffer - 0.5);
      meta.lastItemSlowFails = movement.handItemSimulationFails;
      return;
    }

    evaluateNoSlow(user, movement, meta);
    evaluatePhase(user, movement, meta);
  }

  private void evaluateNoSlow(User user, MovementMetadata movement, Meta meta) {
    int fails = movement.handItemSimulationFails;
    if (fails > meta.lastItemSlowFails && fails >= 2) {
      // Physics increments this only when its own simulation sees ignored item-use slowdown.
      meta.noSlowBuffer += Math.min(1.5, 0.5 + (fails - meta.lastItemSlowFails) * 0.5);
      if (meta.noSlowBuffer >= 2.0) {
        flag(user, "NoSlow", "item-slow simulation fails=" + fails, 2.0);
        meta.noSlowBuffer = 1.0;
      }
    } else if (fails == 0) {
      meta.noSlowBuffer = Math.max(0.0, meta.noSlowBuffer - 0.25);
    }
    meta.lastItemSlowFails = fails;
  }

  private void evaluatePhase(User user, MovementMetadata movement, Meta meta) {
    double dx = movement.positionX - movement.lastPositionX;
    double dz = movement.positionZ - movement.lastPositionZ;
    double horizontalSq = dx * dx + dz * dz;

    boolean tolerance = movement.pistonMotionToleranceRemaining > 0
      || movement.shulkerXToleranceRemaining > 0
      || movement.shulkerYToleranceRemaining > 0
      || movement.shulkerZToleranceRemaining > 0
      || movement.pushedByEntity;
    boolean suspicious = movement.currentlyInBlock
      && horizontalSq > 0.03D * 0.03D
      && (movement.invalidMovement || movement.suspiciousMovement || movement.collidedHorizontally)
      && !tolerance;

    if (!suspicious) {
      meta.phaseBuffer = Math.max(0.0, meta.phaseBuffer - 0.2);
      return;
    }

    meta.phaseBuffer += Math.sqrt(horizontalSq) > 0.12D ? 1.0 : 0.5;
    if (meta.phaseBuffer < 2.5) {
      return;
    }

    flag(user, "Phase",
      "inside-block move dx=" + format(dx) + ", dz=" + format(dz)
        + ", invalid=" + movement.invalidMovement + ", collision=" + movement.collidedHorizontally,
      2.0);
    meta.phaseBuffer = 1.25;
  }

  private void flag(User user, String name, String details, double vl) {
    Violation violation = Violation.builderFor(Physics.class)
      .forPlayer(user.player())
      .withCheckName(name)
      .withMessage("failed " + name.toLowerCase(java.util.Locale.ROOT))
      .withDetails(details)
      .withVL(vl)
      .build();
    Modules.violationProcessor().processViolation(violation);
  }

  private static boolean hardExempt(User user, MovementMetadata movement) {
    AbilityMetadata abilities = user.meta().abilities();
    return movement.isInVehicle()
      || movement.awaitTeleport
      || movement.expectTeleport
      || movement.inRespawnScreen
      || movement.gliding
      || abilities.probablyFlying()
      || abilities.inGameModeIncludePending(SPECTATOR);
  }

  private static String format(double value) {
    return String.format(java.util.Locale.ROOT, "%.4f", value);
  }

  public static final class Meta extends CheckCustomMetadata {
    private int lastItemSlowFails;
    private double noSlowBuffer;
    private double phaseBuffer;
  }
}

package de.jpx3.intave.check.movement.physics;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.CheckSignalConfiguration;
import de.jpx3.intave.check.MetaCheck;
import de.jpx3.intave.check.movement.Physics;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AbilityMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.potion.PotionEffectType;

import static de.jpx3.intave.module.linker.packet.ListenerPriority.LOWEST;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.tracker.player.AbilityTracker.GameMode.SPECTATOR;

/**
 * Independent labels/signals layered on Physics' existing simulator. NoSlow, Phase and Sprint all
 * consume already-computed simulation state instead of implementing competing predictors.
 */
public final class MovementSignalGuard extends MetaCheck<MovementSignalGuard.Meta> {
  private static final PotionEffectType BLINDNESS = PotionEffectType.getByName("BLINDNESS");

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

  @PacketSubscription(priority = LOWEST, packetsIn = ENTITY_ACTION_IN, ignoreCancelled = false)
  public void action(PacketEvent event) {
    if (!(event.delegate() instanceof PacketReceiveEvent)) return;
    WrapperPlayClientEntityAction action = new WrapperPlayClientEntityAction((PacketReceiveEvent) event.delegate());
    if (action.getAction() == WrapperPlayClientEntityAction.Action.START_SPRINTING) {
      metaOf(userOf(event.getPlayer())).startedSprintThisTick = true;
    }
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

    // Sprint has valid/invalid combinations involving gliding, so evaluate it before the general
    // gliding exemption used by Phase and NoSlow.
    evaluateSprint(user, movement, meta);

    if (hardExempt(user, movement)) {
      meta.noSlowBuffer = Math.max(0.0, meta.noSlowBuffer - 0.5);
      meta.phaseBuffer = Math.max(0.0, meta.phaseBuffer - 0.5);
      meta.lastItemSlowFails = movement.handItemSimulationFails;
      meta.startedSprintThisTick = false;
      meta.wasHardHorizontalCollision = movement.collidedHorizontally && movement.suspiciousMovement;
      return;
    }

    evaluateNoSlow(user, movement, meta);
    evaluatePhase(user, movement, meta);
    meta.startedSprintThisTick = false;
    meta.wasHardHorizontalCollision = movement.collidedHorizontally && movement.suspiciousMovement;
  }

  private void evaluateSprint(User user, MovementMetadata movement, Meta meta) {
    AbilityMetadata abilities = user.meta().abilities();
    if (!movement.sprinting) {
      meta.sprintBuffer = Math.max(0.0D, meta.sprintBuffer - 0.25D);
      meta.sprintWallBuffer = Math.max(0.0D, meta.sprintWallBuffer - 0.2D);
      return;
    }

    if (!abilities.allowFlying() && abilities.foodLevel <= 6 && !movement.isInVehicle()) {
      scoreSprint(user, meta, 1.0D, "hunger=" + abilities.foodLevel);
    }

    // SprintB/F have a version-specific vanilla quirk in 1.21.4. Supported clients older than
    // 1.17 do not exist in this project, so the legacy branches are intentionally omitted.
    if (user.protocolVersion() == ProtocolMetadata.VER_1_21_4) {
      boolean piston = movement.pistonMotionToleranceRemaining > 0
        || movement.shulkerXToleranceRemaining > 0
        || movement.shulkerYToleranceRemaining > 0
        || movement.shulkerZToleranceRemaining > 0;
      if (movement.sneaking && movement.inWater && !piston) {
        scoreSprint(user, meta, 0.75D, "sprinting while sneaking in water on 1.21.4");
      }
      if (movement.gliding && movement.lastSprinting) {
        scoreSprint(user, meta, 0.75D, "sprinting while gliding on 1.21.4");
      }
    }

    // Item-slow sprint is also covered by NoSlow; this independent signal catches the state
    // contradiction even when the movement deviation itself remains within Physics tolerance.
    if (movement.handItemSimulationFails >= 2 && movement.inWater) {
      scoreSprint(user, meta, 0.5D, "sprinting while item-use slowdown is active");
    }

    if (BLINDNESS != null && user.player().hasPotionEffect(BLINDNESS)) {
      // Bukkit potion state is not transaction-compensated, so require repetition and never make
      // this signal sufficient by itself for a high VL.
      scoreSprint(user, meta, meta.startedSprintThisTick ? 0.5D : 0.25D, "sprinting with blindness");
    }

    if (meta.wasHardHorizontalCollision && !meta.startedSprintThisTick
      && !movement.inWater && !movement.isInVehicle()) {
      meta.sprintWallBuffer += 1.0D;
      if (meta.sprintWallBuffer >= 2.0D) {
        flag(user, "Sprint", "kept sprinting through hard horizontal collision", 1.5D);
        meta.sprintWallBuffer = 1.0D;
      }
    } else {
      meta.sprintWallBuffer = Math.max(0.0D, meta.sprintWallBuffer - 0.2D);
    }
  }

  private void scoreSprint(User user, Meta meta, double amount, String details) {
    meta.sprintBuffer += amount;
    if (meta.sprintBuffer < 2.0D) return;
    flag(user, "Sprint", details, 1.5D);
    meta.sprintBuffer = 1.0D;
  }

  private void evaluateNoSlow(User user, MovementMetadata movement, Meta meta) {
    int fails = movement.handItemSimulationFails;
    if (fails > meta.lastItemSlowFails && fails >= 2) {
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
    if (!CheckSignalConfiguration.enabled("physics", name)) {
      return;
    }
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
    private boolean startedSprintThisTick;
    private boolean wasHardHorizontalCollision;
    private double sprintBuffer;
    private double sprintWallBuffer;
  }
}

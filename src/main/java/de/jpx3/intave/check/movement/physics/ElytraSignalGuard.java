package de.jpx3.intave.check.movement.physics;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;
import de.jpx3.intave.check.MetaCheck;
import de.jpx3.intave.check.movement.Physics;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.PacketTypes;
import de.jpx3.intave.player.Effects;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import static de.jpx3.intave.module.linker.packet.ListenerPriority.LOWEST;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

/** Layered Elytra invariants that complement Physics' gliding simulator. */
public final class ElytraSignalGuard extends MetaCheck<ElytraSignalGuard.Meta> {
  private static final int TOGGLE_HISTORY = 8;

  public ElytraSignalGuard() {
    super("ElytraSignalGuard", "elytrasignalguard", Meta.class);
  }

  @Override public boolean enabled() { return true; }
  @Override public boolean performLinkage() { return true; }

  @PacketSubscription(priority = LOWEST, packetsIn = ENTITY_ACTION_IN, ignoreCancelled = false)
  public void action(ProtocolPacketEvent event) {
    if (!(event instanceof PacketReceiveEvent)) return;
    WrapperPlayClientEntityAction action = new WrapperPlayClientEntityAction((PacketReceiveEvent) event);
    if (action.getAction() != WrapperPlayClientEntityAction.Action.START_FLYING_WITH_ELYTRA) return;

    User user = userOf(event.getPlayer());
    MovementMetadata movement = user.meta().movement();
    Meta meta = metaOf(user);
    long now = System.currentTimeMillis();
    if (movement.gliding) score(user, meta, 1.0D, "started gliding while already gliding", 2.0D);
    if (user.meta().abilities().flying()) score(user, meta, 1.0D, "started gliding while flying", 2.0D);
    if (movement.onGround && movement.lastOnGround) score(user, meta, 0.9D, "started gliding while grounded", 2.0D);
    if (movement.isInVehicle()) score(user, meta, 1.0D, "started gliding while riding a vehicle", 2.0D);
    if (movement.inWater) score(user, meta, 0.9D, "started gliding while touching water", 2.0D);
    if (hasLevitation(user)) score(user, meta, 1.0D, "started gliding with levitation", 2.0D);
    if (meta.glideThisTick || meta.glideLastTick) score(user, meta, tickingReliably(user) ? 1.0D : 0.5D, "started gliding in consecutive client ticks", 2.0D);
    meta.glideThisTick = true;

    if (!wearingElytra(user)) {
      meta.noElytraBuffer += 1.0D;
      if (meta.noElytraBuffer >= 2.0D) {
        event.setCancelled(true);
        flag(user, "started gliding without an elytra", "chest item is not ELYTRA", 4.0D);
        meta.noElytraBuffer = 1.0D;
      }
    } else meta.noElytraBuffer = Math.max(0.0D, meta.noElytraBuffer - 0.5D);

    if (user.meta().protocol().sendsInputs()) {
      long sinceJump = now - movement.lastTimeJumped;
      if (!movement.clientPressedJump && sinceJump > 250L) {
        meta.jumpBuffer += 0.75D;
        if (meta.jumpBuffer >= 2.25D) {
          flag(user, "started gliding without a valid jump transition", "jump=false, sinceJump=" + sinceJump + "ms", 2.0D);
          meta.jumpBuffer = 1.0D;
        }
      } else meta.jumpBuffer = Math.max(0.0D, meta.jumpBuffer - 0.35D);
    }

    double dx = movement.positionX - movement.lastPositionX;
    double dy = movement.positionY - movement.lastPositionY;
    double dz = movement.positionZ - movement.lastPositionZ;
    double deltaXZ = Math.hypot(dx, dz);
    double deltaY = Math.abs(dy);
    double totalSpeed = Math.hypot(deltaXZ, deltaY);
    double accelXZ = Math.abs(deltaXZ - meta.lastDeltaXZ);
    double accelY = Math.abs(deltaY - meta.lastDeltaY);
    if (totalSpeed >= 0.1D && accelXZ <= 1.0E-7D && accelY <= 1.0E-7D) {
      if (++meta.zeroAccelerationStarts >= 6) {
        flag(user, "repeated impossible elytra reactivation", "accelXZ=" + format(accelXZ) + ", accelY=" + format(accelY), 2.0D);
        meta.zeroAccelerationStarts = 3;
      }
    } else meta.zeroAccelerationStarts = Math.max(0, meta.zeroAccelerationStarts - 1);

    if (meta.lastPositionTime > 0L) {
      long noPositionFor = now - meta.lastPositionTime;
      if (noPositionFor > 500L && ++meta.activationsWithoutPosition >= 3) {
        meta.noPositionBuffer += 1.0D;
        if (meta.noPositionBuffer >= 2.0D) {
          flag(user, "reactivated elytra without position updates", "activations=" + meta.activationsWithoutPosition + ", noPosition=" + noPositionFor + "ms", 2.0D);
          meta.noPositionBuffer = 1.0D;
        }
      }
    }
    recordToggle(meta, now);
    evaluateTogglePattern(user, meta);
  }

  @PacketSubscription(priority = LOWEST, packetsIn = {FLYING, LOOK, POSITION, POSITION_LOOK, CLIENT_TICK_END}, ignoreCancelled = false)
  public void tick(ProtocolPacketEvent event) {
    User user = userOf(event.getPlayer());
    MovementMetadata movement = user.meta().movement();
    Meta meta = metaOf(user);
    PacketTypeCommon type = event.getPacketType();
    long now = System.currentTimeMillis();

    if (type == PacketType.Play.Client.PLAYER_POSITION || type == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION) {
      meta.lastPositionTime = now;
      meta.activationsWithoutPosition = 0;
      meta.noPositionBuffer = Math.max(0.0D, meta.noPositionBuffer - 0.25D);
    }

    boolean modernBoundary = user.meta().protocol().sendsClientTickEnd();
    boolean boundary = modernBoundary ? PacketTypes.isClientEndTick(type) : !PacketTypes.isClientEndTick(type);
    if (!boundary) return;

    double dx = movement.positionX - movement.lastPositionX;
    double dy = movement.positionY - movement.lastPositionY;
    double dz = movement.positionZ - movement.lastPositionZ;
    meta.lastDeltaXZ = Math.hypot(dx, dz);
    meta.lastDeltaY = Math.abs(dy);

    boolean hardExempt = movement.awaitTeleport || movement.expectTeleport || movement.isInVehicle()
      || movement.inWater || movement.inWeb || user.meta().abilities().flying()
      || movement.collidedHorizontally || movement.collidedVertically || movement.pushedByEntity
      || movement.pistonMotionToleranceRemaining > 0 || movement.shulkerXToleranceRemaining > 0
      || movement.shulkerYToleranceRemaining > 0 || movement.shulkerZToleranceRemaining > 0 || hasLevitation(user);

    if (movement.gliding) {
      if (movement.isInVehicle()) score(user, meta, 0.75D, "gliding while riding a vehicle", 2.0D);
      if (movement.onGround && movement.lastOnGround) {
        meta.groundBuffer += 0.5D;
        if (meta.groundBuffer >= 2.0D) {
          flag(user, "remained gliding while grounded", "onGround for consecutive ticks", 1.5D);
          meta.groundBuffer = 1.0D;
        }
      } else meta.groundBuffer = Math.max(0.0D, meta.groundBuffer - 0.25D);
      if (!wearingElytra(user)) {
        meta.noElytraBuffer += 0.5D;
        if (meta.noElytraBuffer >= 2.5D) {
          flag(user, "continued gliding without an elytra", "tracked gliding=true", 3.0D);
          meta.noElytraBuffer = 1.25D;
        }
      }
      if (!hardExempt && !movement.onGround && !movement.lastOnGround) {
        if (Math.abs(dy) < 0.001D) {
          meta.hoverTicks++;
          if (meta.hoverTicks >= 8) {
            meta.hoverBuffer += 0.5D;
            if (meta.hoverBuffer >= 2.0D) {
              flag(user, "hovered while gliding", "deltaY=" + format(dy) + ", ticks=" + meta.hoverTicks, 2.0D);
              meta.hoverBuffer = 1.0D;
            }
          }
        } else {
          meta.hoverTicks = Math.max(0, meta.hoverTicks - 2);
          meta.hoverBuffer = Math.max(0.0D, meta.hoverBuffer - 0.2D);
        }
      } else {
        meta.hoverTicks = 0;
        meta.hoverBuffer = Math.max(0.0D, meta.hoverBuffer - 0.25D);
      }
    } else {
      meta.groundBuffer = Math.max(0.0D, meta.groundBuffer - 0.25D);
      meta.noElytraBuffer = Math.max(0.0D, meta.noElytraBuffer - 0.15D);
      meta.hoverTicks = 0;
      meta.hoverBuffer = Math.max(0.0D, meta.hoverBuffer - 0.15D);
    }

    if (movement.onGround || movement.inWater || movement.awaitTeleport || movement.expectTeleport) {
      meta.airTicks = 0;
      meta.elytraTicks = 0;
      meta.nonElytraTicks = 0;
      meta.toggleCount = 0;
      meta.togglePatternBuffer = Math.max(0.0D, meta.togglePatternBuffer - 0.1D);
    } else {
      meta.airTicks++;
      if (wearingElytra(user)) meta.elytraTicks++;
      else meta.nonElytraTicks++;
      meta.togglePatternBuffer = Math.max(0.0D, meta.togglePatternBuffer - 0.02D);
    }
    meta.glideLastTick = meta.glideThisTick;
    meta.glideThisTick = false;
    meta.generalBuffer = Math.max(0.0D, meta.generalBuffer - 0.05D);
  }

  private void evaluateTogglePattern(User user, Meta meta) {
    if (!tickingReliably(user) || meta.toggleCount < 6 || meta.airTicks < 40) return;
    int n = Math.min(meta.toggleCount, TOGGLE_HISTORY);
    int start = meta.toggleCount - n;
    double sum = 0.0D;
    for (int i = 1; i < n; i++) sum += meta.toggleTimes[(start + i) % TOGGLE_HISTORY] - meta.toggleTimes[(start + i - 1) % TOGGLE_HISTORY];
    int intervals = n - 1;
    if (intervals <= 0) return;
    double mean = sum / intervals;
    double sqDiff = 0.0D;
    for (int i = 1; i < n; i++) {
      double diff = (meta.toggleTimes[(start + i) % TOGGLE_HISTORY] - meta.toggleTimes[(start + i - 1) % TOGGLE_HISTORY]) - mean;
      sqDiff += diff * diff;
    }
    double stdDev = Math.sqrt(sqDiff / intervals);
    double noElytraRatio = meta.airTicks <= 0 ? 0.0D : (double) meta.nonElytraTicks / (double) meta.airTicks;
    if (stdDev < 40.0D && mean >= 50.0D && mean <= 750.0D && noElytraRatio > 0.65D) {
      meta.togglePatternBuffer += 1.0D;
      if (meta.togglePatternBuffer > 3.0D) {
        flag(user, "regular swap-fly toggle pattern", "mean=" + format(mean) + "ms, sd=" + format(stdDev) + "ms, noElytra=" + format(noElytraRatio), 2.0D);
        meta.togglePatternBuffer = 1.5D;
      }
    }
  }

  private static void recordToggle(Meta meta, long now) {
    meta.toggleTimes[meta.toggleCount % TOGGLE_HISTORY] = now;
    meta.toggleCount = (meta.toggleCount + 1) & 0x3FFFFFFF;
  }

  private void score(User user, Meta meta, double amount, String details, double vl) {
    meta.generalBuffer += amount;
    if (meta.generalBuffer < 2.0D) return;
    flag(user, details, details, vl);
    meta.generalBuffer = 1.0D;
  }

  private void flag(User user, String message, String details, double vl) {
    Modules.violationProcessor().processViolation(Violation.builderFor(Physics.class)
      .forPlayer(user.player()).withCheckName("Elytra").withMessage(message).withDetails(details).withVL(vl).build());
  }

  private static boolean wearingElytra(User user) {
    try {
      ItemStack chest = user.player().getInventory().getChestplate();
      return chest != null && chest.getType() == Material.ELYTRA;
    } catch (Throwable ignored) { return true; }
  }

  private static boolean hasLevitation(User user) {
    try { return Effects.levitationEffectActive(user.player()); }
    catch (Throwable ignored) { return false; }
  }

  private static boolean tickingReliably(User user) {
    double average = user.meta().connection().averageMovementPacketTimestamp();
    return average > 0.0D && average <= 90.0D;
  }

  private static String format(double value) { return String.format(java.util.Locale.ROOT, "%.4f", value); }

  public static final class Meta extends CheckCustomMetadata {
    private final long[] toggleTimes = new long[TOGGLE_HISTORY];
    private boolean glideThisTick;
    private boolean glideLastTick;
    private double generalBuffer;
    private double noElytraBuffer;
    private double jumpBuffer;
    private double groundBuffer;
    private int hoverTicks;
    private double hoverBuffer;
    private double lastDeltaXZ;
    private double lastDeltaY;
    private int zeroAccelerationStarts;
    private long lastPositionTime;
    private int activationsWithoutPosition;
    private double noPositionBuffer;
    private int airTicks;
    private int elytraTicks;
    private int nonElytraTicks;
    private int toggleCount;
    private double togglePatternBuffer;
  }
}

package de.jpx3.intave.check.movement.physics;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.movement.Physics;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.PacketTypes;
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
 * Supplemental detector for clients that keep sending tick/rotation traffic while withholding
 * meaningful position updates in mid-air. It deliberately requires the last real movement to be
 * downward, avoiding broad "not moving in air" heuristics that are prone to false positives.
 */
public final class AirStuckGuard extends MetaCheckPart<Physics, AirStuckGuard.Meta> {
  private static final double MIN_REAL_DISTANCE_SQ = 0.01 * 0.01;
  private final boolean enabled;
  private final long maxGapMs;
  private final long flagCooldownMs;

  public AirStuckGuard(Physics parentCheck) {
    super(parentCheck, Meta.class);
    this.enabled = parentCheck.configuration().settings().boolBy("air-stuck.enabled", true);
    this.maxGapMs = parentCheck.configuration().settings().longInBoundsBy("air-stuck.max-gap-ms", 1500, 10000, 2500);
    this.flagCooldownMs = parentCheck.configuration().settings().longInBoundsBy("air-stuck.flag-cooldown-ms", 1000, 15000, 4000);
  }

  @Override
  public boolean enabled() {
    return super.enabled() && enabled;
  }

  @PacketSubscription(
    priority = LOWEST,
    ignoreCancelled = false,
    packetsIn = {FLYING, LOOK, POSITION, POSITION_LOOK, CLIENT_TICK_END}
  )
  public void receive(PacketEvent event) {
    User user = userOf(event.getPlayer());
    Meta meta = metaOf(user);
    PacketType type = event.getPacketType();

    if (!PacketTypes.isClientEndTick(type)) {
      PlayerMoveReader reader = PacketReaders.readerOf(event.getPacket());
      try {
        if (reader.hasMovement()) {
          double x = reader.positionX();
          double y = reader.positionY();
          double z = reader.positionZ();
          if (!meta.hasAnchor) {
            setAnchor(meta, x, y, z);
          } else {
            double dx = x - meta.anchorX;
            double dy = y - meta.anchorY;
            double dz = z - meta.anchorZ;
            if (dx * dx + dy * dy + dz * dz > MIN_REAL_DISTANCE_SQ) {
              meta.lastRealDeltaY = y - meta.anchorY;
              setAnchor(meta, x, y, z);
            }
          }
        }
      } finally {
        reader.release();
      }
    }

    checkTimeout(user, meta);
  }

  private void checkTimeout(User user, Meta meta) {
    if (!meta.hasAnchor) {
      return;
    }

    long now = System.currentTimeMillis();
    long elapsed = now - meta.lastRealPositionAt;
    if (elapsed <= maxGapMs || now - meta.lastFlagAt < flagCooldownMs) {
      return;
    }

    MovementMetadata movement = user.meta().movement();
    AbilityMetadata abilities = user.meta().abilities();

    if (movement.onGround || movement.lastOnGround
      || movement.isInVehicle()
      || movement.gliding
      || movement.inWater
      || movement.inWeb
      || movement.onLadderLast
      || movement.interactingFluid != null
      || movement.awaitTeleport
      || movement.expectTeleport
      || movement.inRespawnScreen
      || abilities.probablyFlying()
      || abilities.inGameModeIncludePending(SPECTATOR)) {
      resetTimer(meta, now);
      return;
    }

    // Require evidence that the player was actually descending before position updates stopped.
    // This intentionally misses some cheats in exchange for avoiding ghost-block / edge-state falses.
    boolean falling = meta.lastRealDeltaY < -0.01 || movement.baseMotionY < -0.02;
    if (!falling) {
      return;
    }

    Violation violation = Violation.builderFor(Physics.class)
      .forPlayer(user.player())
      .withMessage("withheld position updates while falling")
      .withDetails("gap=" + elapsed + "ms, dy=" + String.format("%.4f", meta.lastRealDeltaY))
      .withVL(2)
      .build();
    Modules.violationProcessor().processViolation(violation);

    meta.lastFlagAt = now;
    resetTimer(meta, now);
  }

  private static void setAnchor(Meta meta, double x, double y, double z) {
    meta.anchorX = x;
    meta.anchorY = y;
    meta.anchorZ = z;
    meta.lastRealPositionAt = System.currentTimeMillis();
    meta.hasAnchor = true;
  }

  private static void resetTimer(Meta meta, long now) {
    meta.lastRealPositionAt = now;
  }

  public static final class Meta extends CheckCustomMetadata {
    private boolean hasAnchor;
    private double anchorX, anchorY, anchorZ;
    private double lastRealDeltaY;
    private long lastRealPositionAt;
    private long lastFlagAt;
  }
}

package de.jpx3.intave.check.combat.heuristics.modern;

import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.module.tracker.entity.EntityTracker;
import de.jpx3.intave.packet.PacketTypes;
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.player.Effects;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.GameMode;

import java.util.ArrayDeque;
import java.util.Deque;

import static de.jpx3.intave.module.linker.packet.ListenerPriority.HIGH;
import static de.jpx3.intave.module.linker.packet.ListenerPriority.NORMAL;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

/**
 * Independent combat-behaviour evidence layered on top of Reach/HitBox and classic Heuristics.
 * It deliberately does not cancel attacks: AttackRaytrace remains authoritative for mitigation.
 */
public final class CombatBehaviorHeuristic extends ModernCombatHeuristic<CombatBehaviorHeuristic.Meta> {
  private static final int MAX_PENDING_ATTACKS = 10;
  private static final double ANGLE_THRESHOLD = 45.0D;
  private static final double CLOSE_TARGET_DISTANCE = 0.50D;
  private static final double MICRO_FALL_DISTANCE = 0.07D;

  public CombatBehaviorHeuristic(Heuristics parentCheck) {
    super(parentCheck, Meta.class);
  }

  @PacketSubscription(priority = HIGH, packetsIn = {ATTACK_ENTITY, USE_ENTITY}, ignoreCancelled = false)
  public void attack(ProtocolPacketEvent event) {
    EntityUseReader reader = PacketReaders.readerOf(event);
    try {
      if (!reader.isAttackPacket()) return;

      User user = userOf(event.getPlayer());
      MovementMetadata movement = user.meta().movement();
      Meta meta = metaOf(user);
      if (hardExempt(user, movement)) {
        meta.angleBuffer = Math.max(0.0D, meta.angleBuffer - 0.5D);
        meta.criticalBuffer = Math.max(0.0D, meta.criticalBuffer - 0.5D);
        meta.zeroPitchBuffer = Math.max(0.0D, meta.zeroPitchBuffer - 0.5D);
        meta.pendingTargets.clear();
        return;
      }

      Entity target = EntityTracker.entityByIdentifier(user, reader.entityId());
      if (target == null || !target.isPlayer || !target.isEntityAlive() || target.boundingBox() == null) {
        return;
      }

      if (meta.pendingTargets.size() < MAX_PENDING_ATTACKS) {
        meta.pendingTargets.addLast(reader.entityId());
      }

      checkMicroCritical(user, movement, meta, reader.entityId());
    } finally {
      reader.release();
    }
  }

  @PacketSubscription(
    priority = NORMAL,
    packetsIn = {FLYING, LOOK, POSITION, POSITION_LOOK, CLIENT_TICK_END},
    ignoreCancelled = false
  )
  public void tick(ProtocolPacketEvent event) {
    User user = userOf(event.getPlayer());
    PacketTypeCommon type = event.getPacketType();
    boolean endTick = PacketTypes.isClientEndTick(type);
    if (user.meta().protocol().sendsClientTickEnd()) {
      if (!endTick) return;
    } else if (endTick) {
      return;
    }

    Meta meta = metaOf(user);
    MovementMetadata movement = user.meta().movement();
    if (hardExempt(user, movement)) {
      meta.pendingTargets.clear();
      meta.angleBuffer = Math.max(0.0D, meta.angleBuffer - 0.5D);
      meta.zeroPitchBuffer = Math.max(0.0D, meta.zeroPitchBuffer - 0.5D);
      return;
    }

    boolean hadAttack = !meta.pendingTargets.isEmpty();
    while (!meta.pendingTargets.isEmpty()) {
      int entityId = meta.pendingTargets.removeFirst();
      Entity target = EntityTracker.entityByIdentifier(user, entityId);
      if (target == null || !target.isPlayer || !target.isEntityAlive() || target.boundingBox() == null) {
        continue;
      }
      checkAttackAngle(user, movement, meta, target);
    }

    if (hadAttack) {
      checkZeroPitch(user, movement, meta);
    } else {
      meta.zeroPitchBuffer = Math.max(0.0D, meta.zeroPitchBuffer - 0.10D);
    }
  }

  private void checkAttackAngle(User user, MovementMetadata movement, Meta meta, Entity target) {
    BoundingBox box = target.boundingBox();
    double eyeX = movement.positionX;
    double eyeY = movement.positionY + movement.eyeHeight();
    double eyeZ = movement.positionZ;

    double nearestX = clamp(eyeX, box.minX, box.maxX);
    double nearestY = clamp(eyeY, box.minY, box.maxY);
    double nearestZ = clamp(eyeZ, box.minZ, box.maxZ);
    double nearestDistance = distance(eyeX, eyeY, eyeZ, nearestX, nearestY, nearestZ);
    if (nearestDistance < CLOSE_TARGET_DISTANCE) {
      meta.angleBuffer = Math.max(0.0D, meta.angleBuffer - 0.25D);
      return;
    }

    double yaw = Math.toRadians(movement.rotationYaw);
    double pitch = Math.toRadians(movement.rotationPitch);
    double lookX = -Math.sin(yaw) * Math.cos(pitch);
    double lookY = -Math.sin(pitch);
    double lookZ = Math.cos(yaw) * Math.cos(pitch);

    // Test the closest point, centre and all corners. Taking the best angle is pessimistic in the
    // player's favour and avoids flagging merely because the target is large or scaled.
    double minAngle = angleToPoint(eyeX, eyeY, eyeZ, lookX, lookY, lookZ, nearestX, nearestY, nearestZ);
    minAngle = Math.min(minAngle, angleToPoint(eyeX, eyeY, eyeZ, lookX, lookY, lookZ,
      (box.minX + box.maxX) * 0.5D, (box.minY + box.maxY) * 0.5D, (box.minZ + box.maxZ) * 0.5D));

    double[] xs = {box.minX, box.maxX};
    double[] ys = {box.minY, box.maxY};
    double[] zs = {box.minZ, box.maxZ};
    for (double x : xs) {
      for (double y : ys) {
        for (double z : zs) {
          minAngle = Math.min(minAngle, angleToPoint(eyeX, eyeY, eyeZ, lookX, lookY, lookZ, x, y, z));
        }
      }
    }

    if (minAngle >= ANGLE_THRESHOLD && tickingReliably(user)) {
      meta.angleBuffer += 1.0D;
      if (meta.angleBuffer >= 2.0D) {
        flag(user, "target-angle",
          "angle=" + format(minAngle) + "deg target=" + target.entityId() + " distance=" + format(nearestDistance),
          3.0D);
        meta.angleBuffer = 1.0D;
      }
    } else {
      meta.angleBuffer = Math.max(0.0D, meta.angleBuffer - 0.35D);
    }
  }

  private void checkZeroPitch(User user, MovementMetadata movement, Meta meta) {
    if (Float.floatToIntBits(movement.rotationPitch) == Float.floatToIntBits(0.0F) && tickingReliably(user)) {
      meta.zeroPitchBuffer += 1.0D;
      if (meta.zeroPitchBuffer >= 4.0D) {
        flag(user, "aim-zero-pitch",
          "pitch=0.0 yaw=" + format(movement.rotationYaw) + " repeated=" + format(meta.zeroPitchBuffer),
          1.5D);
        meta.zeroPitchBuffer = 2.0D;
      }
    } else {
      meta.zeroPitchBuffer = Math.max(0.0D, meta.zeroPitchBuffer - 0.35D);
    }
  }

  private void checkMicroCritical(User user, MovementMetadata movement, Meta meta, int targetId) {
    double fallDistance = movement.artificialFallDistance;
    boolean suspicious = !movement.onGround
      && !movement.lastOnGround
      && fallDistance > 0.0D
      && fallDistance < MICRO_FALL_DISTANCE
      && movement.baseMotionY < -0.001D
      && !movement.collidedVertically
      && !movement.inWater
      && !movement.inWeb
      && !movement.gliding
      && !user.meta().abilities().flying()
      && !movement.isInVehicle()
      && !movement.pushedByEntity
      && movement.pistonMotionToleranceRemaining <= 0
      && movement.shulkerYToleranceRemaining <= 0
      && !hasVerticalPotionUncertainty(user);

    if (!suspicious) {
      meta.criticalBuffer = Math.max(0.0D, meta.criticalBuffer - 0.25D);
      return;
    }

    meta.criticalBuffer += 1.0D;
    if (meta.criticalBuffer >= 3.0D && tickingReliably(user)) {
      flag(user, "critical-fall",
        "fallDistance=" + format(fallDistance) + " motionY=" + format(movement.baseMotionY)
          + " target=" + targetId,
        2.0D);
      meta.criticalBuffer = 1.5D;
    }
  }

  private static boolean hardExempt(User user, MovementMetadata movement) {
    return movement.recordedMoves < 40
      || movement.awaitTeleport
      || movement.expectTeleport
      || movement.isInVehicle()
      || movement.gliding
      || user.meta().abilities().ignoringMovementPackets()
      || user.meta().abilities().inGameMode(GameMode.CREATIVE)
      || user.meta().abilities().inGameMode(GameMode.SPECTATOR);
  }

  private static boolean hasVerticalPotionUncertainty(User user) {
    try {
      return Effects.levitationEffectActive(user.player()) || Effects.slowFallingEffectActive(user.player());
    } catch (Throwable ignored) {
      return true;
    }
  }

  private static boolean tickingReliably(User user) {
    double average = user.meta().connection().averageMovementPacketTimestamp();
    return average > 0.0D && average <= 90.0D;
  }

  private static double angleToPoint(
    double eyeX, double eyeY, double eyeZ,
    double lookX, double lookY, double lookZ,
    double x, double y, double z
  ) {
    double dx = x - eyeX;
    double dy = y - eyeY;
    double dz = z - eyeZ;
    double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
    if (length <= 1.0E-9D) return 0.0D;
    double dot = (dx * lookX + dy * lookY + dz * lookZ) / length;
    dot = Math.max(-1.0D, Math.min(1.0D, dot));
    return Math.toDegrees(Math.acos(dot));
  }

  private static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }

  private static double distance(double x1, double y1, double z1, double x2, double y2, double z2) {
    double dx = x2 - x1;
    double dy = y2 - y1;
    double dz = z2 - z1;
    return Math.sqrt(dx * dx + dy * dy + dz * dz);
  }

  private static String format(double value) {
    return String.format(java.util.Locale.ROOT, "%.4f", value);
  }

  public static final class Meta extends CheckCustomMetadata {
    private final Deque<Integer> pendingTargets = new ArrayDeque<>();
    private double angleBuffer;
    private double criticalBuffer;
    private double zeroPitchBuffer;
  }
}
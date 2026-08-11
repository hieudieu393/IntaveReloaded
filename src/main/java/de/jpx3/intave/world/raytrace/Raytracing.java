/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.world.raytrace;

import de.jpx3.intave.check.movement.physics.environment.Pose;
import de.jpx3.intave.diagnostic.timings.Timings;
import de.jpx3.intave.math.SinusCache;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.player.ActionBar;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.MovingObjectPosition;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.RawVector3d;
import de.jpx3.intave.user.MessageChannel;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.world.Particles;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.SNEAKING;

public final class Raytracing {
  private static final Raytracer RAYTRACER = new UniversalRaytracer();
  private static final boolean[] PESSIMISTIC_BOOLEAN_ORDER = new boolean[]{false, true};
  private static final double MAX_TRACKED_INTERACTION_RANGE = 64.0D;
  // AttackRaytrace historically uses reach == 10 as its miss sentinel. Once a custom range would
  // make the ray itself approach that sentinel, preserve safety by treating a pure miss as neutral;
  // actual intersections beyond the configured range are still returned and classified as REACH.
  private static final double LEGACY_MISS_SENTINEL_SAFE_RANGE = 9.0D;

  public static float reachDistanceOf(Player player) {
    return reachDistanceOf(UserRepository.userOf(player));
  }

  public static float reachDistanceOf(User user) {
    return reachDistanceOf(user.meta());
  }

  public static float reachDistanceOf(MetadataBundle meta) {
    float fallback = meta.abilities().inGameMode(GameMode.CREATIVE) ? 5.0F : 3.0F;
    if (!meta.protocol().supportsInteractionRangeAttributes()) {
      return fallback;
    }

    double attribute = meta.abilities().attributeValue("player.entity_interaction_range");
    if (!Double.isFinite(attribute) || attribute <= 0.0D) {
      return fallback;
    }

    // AbilityMetadata seeds vanilla survival values before UPDATE_ATTRIBUTES is transaction-synced.
    // Never shrink the historic Intave allowance during that short bootstrap window; server-side
    // custom attributes that increase reach are honored immediately once tracked.
    return (float) Math.min(MAX_TRACKED_INTERACTION_RANGE, Math.max(fallback, attribute));
  }

  /**
   * Calculates the reach with and without mouse delay fix and returns the smallest calculated reach
   *
   * @return
   */
  public static Raytrace doubleMDFBlockConstraintEntityRaytrace(
    Player player, Entity entity, boolean alternativePositionY,
    double lastPositionX, double lastPositionY, double lastPositionZ,
    float lastRotationYaw,
    float rotationYaw, float rotationPitch,
    double expandHitbox, boolean withoutMouseDelayFix
  ) {
    double blockReachDistance = Raytracing.reachDistanceOf(player);

    Raytrace distanceOfResult = blockConstraintEntityRaytrace(
      player,
      entity, alternativePositionY,
      lastPositionX, lastPositionY, lastPositionZ,
      rotationYaw, rotationPitch,
      expandHitbox
    );
    if (withoutMouseDelayFix && distanceOfResult.reach() > blockReachDistance && rotationYaw != lastRotationYaw) {
      distanceOfResult = blockConstraintEntityRaytrace(
        player,
        entity, alternativePositionY,
        lastPositionX, lastPositionY, lastPositionZ,
        lastRotationYaw, rotationPitch,
        expandHitbox
      );
    }

    return distanceOfResult;
  }

  /**
   * @param expandBoundingBox should be "0.1f" for a default hitbox
   */
  public static Raytrace blockConstraintEntityRaytrace(
    Player player, Entity entity,
    boolean useAlternativePositionY,
    double prevPosX, double prevPosY, double prevPosZ,
    float prevYaw, float pitch,
    double expandBoundingBox
  ) {
    return entityRaytrace(
      player,
      entity.boundingBox(),
      useAlternativePositionY ? (entity.alternativePosition.posY - entity.position.posY) : 0,
      prevPosX, prevPosY, prevPosZ,
      prevYaw, pitch,
      expandBoundingBox,
      EntityRaytraceBlockConstraint.ACCEPT_BLOCKS
    );
  }

  /**
   * @param expandBoundingBox should be "0.1f" for a default hitbox
   */
  public static Raytrace blockIgnoringEntityRaytrace(
    Player player, Entity entity,
    boolean useAlternativePositionY,
    double prevPosX, double prevPosY, double prevPosZ,
    float prevYaw, float pitch,
    double expandBoundingBox
  ) {
    return entityRaytrace(
      player,
      entity.boundingBox(),
      useAlternativePositionY ? (entity.alternativePosition.posY - entity.position.posY) : 0,
      prevPosX, prevPosY, prevPosZ,
      prevYaw, pitch,
      expandBoundingBox,
      EntityRaytraceBlockConstraint.IGNORE_BLOCKS
    );
  }

  /**
   * Takes a entity and returns the range between the player and the entity. (Client side its called "getMouseOver" and
   * is from EntityRenderer.java)
   *
   * @return distance the distance between the entity and the eyes of the player 0 means the player is inside of the
   * entity -1 means the player hit outside the hitbox of the entity greater than 0 means the reach of the player
   */
  public static Raytrace entityRaytrace(
    Player player,
    BoundingBox entityBoundingBox,
    double alternativeYDifference,
    double prevPosX, double prevPosY, double prevPosZ,
    float prevYaw, float pitch,
    double boundingBoxExpansion,
    EntityRaytraceBlockConstraint rayTraceBlocks
  ) {
    Timings.SERVICE_RAYTRACER_ENTITY.start();
    double attackReachDistance = reachDistanceOf(player);
    double rayLength = Math.max(6.0D, Math.min(MAX_TRACKED_INTERACTION_RANGE + 1.0D, attackReachDistance + 1.0D));
    boolean extendedRange = attackReachDistance >= LEGACY_MISS_SENTINEL_SAFE_RANGE;
    double missDistance = extendedRange ? rayLength + 1.0D : 10.0D;
    double lastReach = missDistance;
    RawVector3d lastHitVec = null;
    RawVector3d lastEyeVector = null;

    User user = UserRepository.userOf(player);
    Pose assumedPose = user.meta().movement().pose();
    boolean sneakUncertainty = user.meta().protocol().delayedSneak() &&
      user.meta().movement().ticksPast(SNEAKING) <= 2 &&
      assumedPose == Pose.STANDING;

    for (int i = 0; i < 2; i++) {
      Pose selectedPose;
      if (i == 0) {
        selectedPose = assumedPose;
      } else if (sneakUncertainty && lastReach >= attackReachDistance) {
        selectedPose = Pose.CROUCHING;
      } else {
        continue;
      }

      RawVector3d eyeVector = positionEyes(player, selectedPose, prevPosX, prevPosY, prevPosZ);

      for (boolean fastMath : PESSIMISTIC_BOOLEAN_ORDER) {
        if (lastReach < attackReachDistance)
          break;

        if (lastEyeVector == null) {
          lastEyeVector = eyeVector;
        }

        RawVector3d interpolatedLookVec = wrappedVectorForRotation(pitch, prevYaw, fastMath);
        RawVector3d lookVector = eyeVector.addVector(
          interpolatedLookVec.x() * rayLength,
          interpolatedLookVec.y() * rayLength,
          interpolatedLookVec.z() * rayLength
        );
        BoundingBox hitBox = entityBoundingBox.grow(boundingBoxExpansion, boundingBoxExpansion, boundingBoxExpansion);
        if (alternativeYDifference != 0) {
          hitBox = hitBox.addJustMaxY(alternativeYDifference);
        }
        MovingObjectPosition movingObjectPosition = hitBox.calculateIntercept(eyeVector, lookVector);
        if (hitBox.isVecInside(eyeVector)) {
          lastReach = 0;
          lastHitVec = null;
          lastEyeVector = null;
        } else if (movingObjectPosition != null) {
          double distanceToEntity = eyeVector.distanceTo(movingObjectPosition.hitVec);
          double reach;
          boolean blockRaytrace = false;
          if (rayTraceBlocks == EntityRaytraceBlockConstraint.ACCEPT_BLOCKS) {
            MovingObjectPosition blockMovingPosition = Raytracing.blockRayTrace(player.getWorld(), player, eyeVector, lookVector);
            double distanceToBlock = blockMovingPosition == null || blockMovingPosition.hitVec == null
              ? missDistance : eyeVector.distanceTo(blockMovingPosition.hitVec);
            reach = distanceToBlock < distanceToEntity ? missDistance : distanceToEntity;
            blockRaytrace = true;
          } else {
            reach = distanceToEntity;
          }
          if (reach < lastReach && (reach < attackReachDistance || blockRaytrace)) {
            lastReach = reach;
            lastEyeVector = eyeVector;
            lastHitVec = movingObjectPosition.hitVec;
          }
        }
      }
    }

    if (lastEyeVector == null) {
      lastEyeVector = positionEyes(player, Pose.STANDING, prevPosX, prevPosY, prevPosZ);
    }

    // For extended ranges the enclosing AttackRaytrace still has a legacy numeric MISS sentinel.
    // Returning a neutral result for a pure miss prevents a legitimate custom range from becoming a
    // false REACH/MISS. If an entity was actually intersected outside the allowed range, lastHitVec is
    // non-null and the real distance is preserved, so the normal REACH path remains fully active.
    double reportedReach = extendedRange && lastHitVec == null && lastReach > attackReachDistance
      ? 0.0D : lastReach;

    Timings.SERVICE_RAYTRACER_ENTITY.stop();
    return Raytrace.ofNative(lastEyeVector, lastHitVec, reportedReach);
  }

  private static RawVector3d wrappedVectorForRotation(float pitch, float prevYaw, boolean fastMath) {
    float var3 = SinusCache.cos(-prevYaw * 0.017453292f - (float) Math.PI, fastMath);
    float var4 = SinusCache.sin(-prevYaw * 0.017453292F - (float) Math.PI, fastMath);
    float var5 = -SinusCache.cos(-pitch * 0.017453292f, fastMath);
    float var6 = SinusCache.sin(-pitch * 0.017453292f, fastMath);
    return new RawVector3d(var4 * var5, var6, var3 * var5);
  }

  private static RawVector3d positionEyes(Player player, Pose pose, double prevPosX, double prevPosY, double prevPosZ) {
    return new RawVector3d(prevPosX, prevPosY + resolvePlayerEyeHeight(player, pose), prevPosZ);
  }

  public static MovingObjectPosition blockRayTrace(Player player, Location playerLocation, Pose pose) {
    double blockReachDistance = resolveBlockReachDistance(player);
    double eyeHeight = resolvePlayerEyeHeight(player, pose);
    return blockRayTrace(player, playerLocation, playerLocation, blockReachDistance, eyeHeight, 1.0f);
  }

  public static MovingObjectPosition blockRayTrace(Player player, Location location, Location prevLocation, double blockReachDistance, double eyeHeight, float partialTicks) {
    RawVector3d eyeVector = resolvePositionEyes(location, prevLocation, eyeHeight, partialTicks);
    RawVector3d vec4 = resolveLookVector(location, prevLocation, partialTicks);
    RawVector3d targetVector = eyeVector.addVector(vec4.x() * blockReachDistance, vec4.y() * blockReachDistance, vec4.z() * blockReachDistance);
    User user = UserRepository.userOf(player);
    if (user.receives(MessageChannel.DEBUG_HITRAY)) {
      List<Position> positions = new ArrayList<>();
      for (int i = 0; i < 10; i++) {
        double t = i / 10.0;
        Position position = new Position(
          eyeVector.x() + (targetVector.x() - eyeVector.x()) * t,
          eyeVector.y() + (targetVector.y() - eyeVector.y()) * t,
          eyeVector.z() + (targetVector.z() - eyeVector.z()) * t
        );
        positions.add(position);
      }
      for (Position position : positions) {
        Particles.spawnVillagerHappyParticleAt(user, position);
      }
      ActionBar.sendActionBar(
        player,
        eyeVector + " " + location.getY() + " " + user.meta().movement().rotationPitch
      );
    }
    return blockRayTrace(location.getWorld(), player, eyeVector, targetVector);
  }

  public static MovingObjectPosition blockRayTrace(World world, Player player, RawVector3d eyeVector, RawVector3d targetVector) {
    try {
      Timings.SERVICE_RAYTRACER_BLOCK.start();
      return Raytracing.RAYTRACER.raytrace(world, player, eyeVector, targetVector);
    } finally {
      Timings.SERVICE_RAYTRACER_BLOCK.stop();
    }
  }

  public static RawVector3d resolvePositionEyes(Location location, Location prevLocation, double eyeHeight, float partialTicks) {
    double posX = location.getX();
    double posY = location.getY();
    double posZ = location.getZ();
    if (partialTicks == 1.0f) {
      return new RawVector3d(posX, posY + eyeHeight, posZ);
    }
    double prevPosX = prevLocation.getX();
    double prevPosY = prevLocation.getY();
    double prevPosZ = prevLocation.getZ();
    double d0 = prevPosX + (posX - prevPosX) * partialTicks;
    double d2 = prevPosY + (posY - prevPosY) * partialTicks + eyeHeight;
    double d3 = prevPosZ + (posZ - prevPosZ) * partialTicks;
    return new RawVector3d(d0, d2, d3);
  }

  private static RawVector3d resolveLookVector(Location location, Location prevLocation, float partialTicks) {
    float rotationYawHead = location.getYaw();
    float rotationPitch = location.getPitch();
    if (partialTicks == 1.0f) {
      return resolveVectorForRotation(rotationPitch, rotationYawHead);
    }
    float prevRotationYawHead = prevLocation.getYaw();
    float prevRotationPitch = prevLocation.getPitch();
    float f = prevRotationPitch + (rotationPitch - prevRotationPitch) * partialTicks;
    float f2 = prevRotationYawHead + (rotationYawHead - prevRotationYawHead) * partialTicks;
    return resolveVectorForRotation(f, f2);
  }

  private static RawVector3d resolveVectorForRotation(float pitch, float yaw) {
    float f = SinusCache.cos(-yaw * 0.017453292f - 3.1415927f, false);
    float f2 = SinusCache.sin(-yaw * 0.017453292F - 3.1415927f, false);
    float f3 = -SinusCache.cos(-pitch * 0.017453292f, false);
    float f4 = SinusCache.sin(-pitch * 0.017453292f, false);
    return new RawVector3d(f2 * f3, f4, f * f3);
  }

  public static double resolvePlayerEyeHeight(Player player) {
    User user = UserRepository.userOf(player);
    return user.meta().movement().eyeHeight();
  }

  public static double resolvePlayerEyeHeight(Player player, Pose pose) {
    User user = UserRepository.userOf(player);
    return user.meta().movement().eyeHeight(pose);
  }

  private static double resolveBlockReachDistance(Player player) {
    User user = UserRepository.userOf(player);
    double fallback = player.getGameMode() == GameMode.CREATIVE ? 5.0D : 4.5D;
    if (!user.meta().protocol().supportsInteractionRangeAttributes()) {
      return fallback;
    }

    double attribute = user.meta().abilities().attributeValue("player.block_interaction_range");
    if (!Double.isFinite(attribute) || attribute <= 0.0D) {
      return fallback;
    }
    return Math.min(MAX_TRACKED_INTERACTION_RANGE, Math.max(fallback, attribute));
  }
}

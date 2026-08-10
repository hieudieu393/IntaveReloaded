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

  public static float reachDistanceOf(Player player) {
    return reachDistanceOf(UserRepository.userOf(player));
  }

  public static float reachDistanceOf(User user) {
    return reachDistanceOf(user.meta());
  }

  public static float reachDistanceOf(MetadataBundle meta) {
    boolean creative = meta.abilities().inGameMode(GameMode.CREATIVE);
    float vanillaFallback = creative ? 5.0F : 3.0F;

    // Minecraft 1.21+ exposes entity interaction reach as an attribute. AbilityMetadata seeds the
    // vanilla survival baseline so AttributeTracker can transaction-compensate subsequent server
    // changes. This makes Reach honor plugins/modifiers that legitimately change attack distance.
    double tracked = meta.abilities().attributeValue("player.entity_interaction_range");
    if (!Double.isFinite(tracked) || tracked <= 0.0D) {
      return vanillaFallback;
    }

    // Before the first compensated attribute update, creative still has the old Intave 5-block
    // fallback while the seeded generic value is 3.0. Preserve that behavior instead of silently
    // shrinking creative reach during login/world transitions.
    if (creative && Math.abs(tracked - 3.0D) < 1.0E-6D) {
      return vanillaFallback;
    }

    // Do not allow corrupt/extreme attribute packets to make the raytracer search unbounded. The
    // attribute itself is still tracked; this cap is only a defensive computational limit.
    return (float) Math.min(tracked, 64.0D);
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
//    float rotationYaw = movementData.rotationYaw % 360;

    // mouse delay fix
    Raytrace distanceOfResult = blockConstraintEntityRaytrace(
      player,
      entity, alternativePositionY,
      lastPositionX, lastPositionY, lastPositionZ,
      rotationYaw, rotationPitch,
      expandHitbox
    );
    if (withoutMouseDelayFix && distanceOfResult.reach() > blockReachDistance && rotationYaw != lastRotationYaw) {
      // normal
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
    double blockReachDistance = 6;
    double attackReachDistance = reachDistanceOf(player);
    double lastReach = 10;
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
          interpolatedLookVec.x() * blockReachDistance,
          interpolatedLookVec.y() * blockReachDistance,
          interpolatedLookVec.z() * blockReachDistance
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
            double distanceToBlock = blockMovingPosition == null || blockMovingPosition.hitVec == null ? 10 : eyeVector.distanceTo(blockMovingPosition.hitVec);
            reach = distanceToBlock < distanceToEntity ? 10 : distanceToEntity;
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

    Timings.SERVICE_RAYTRACER_ENTITY.stop();
    return Raytrace.ofNative(lastEyeVector, lastHitVec, lastReach);
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
    double blockReachDistance = resolveBlockReachDistance(player.getGameMode());
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
        Particles.displayBlockCrack(position, player, org.bukkit.Material.REDSTONE_BLOCK);
      }
    }
    return RAYTRACER.rayTraceBlock(player.getWorld(), player, eyeVector, targetVector);
  }

  public static MovingObjectPosition blockRayTrace(World world, Player player, RawVector3d eyeVector, RawVector3d targetVector) {
    return RAYTRACER.rayTraceBlock(world, player, eyeVector, targetVector);
  }

  private static RawVector3d resolvePositionEyes(Location location, Location prevLocation, double eyeHeight, float partialTicks) {
    double d0 = prevLocation.getX() + (location.getX() - prevLocation.getX()) * partialTicks;
    double d1 = prevLocation.getY() + (location.getY() - prevLocation.getY()) * partialTicks + eyeHeight;
    double d2 = prevLocation.getZ() + (location.getZ() - prevLocation.getZ()) * partialTicks;
    return new RawVector3d(d0, d1, d2);
  }

  private static RawVector3d resolveLookVector(Location location, Location prevLocation, float partialTicks) {
    float var1 = prevLocation.getPitch() + (location.getPitch() - prevLocation.getPitch()) * partialTicks;
    float var2 = prevLocation.getYaw() + (location.getYaw() - prevLocation.getYaw()) * partialTicks;
    float f = SinusCache.cos(-var2 * 0.017453292F - (float) Math.PI, false);
    float f1 = SinusCache.sin(-var2 * 0.017453292F - (float) Math.PI, false);
    float f2 = -SinusCache.cos(-var1 * 0.017453292F, false);
    float f3 = SinusCache.sin(-var1 * 0.017453292F, false);
    return new RawVector3d(f1 * f2, f3, f * f2);
  }

  private static double resolvePlayerEyeHeight(Player player, Pose pose) {
    return pose.eyeHeight(UserRepository.userOf(player));
  }

  private static float resolveBlockReachDistance(GameMode gameMode) {
    return gameMode == GameMode.CREATIVE ? 5.0f : 4.5f;
  }
}

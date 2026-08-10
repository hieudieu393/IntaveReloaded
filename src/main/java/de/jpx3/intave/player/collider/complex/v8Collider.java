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

package de.jpx3.intave.player.collider.complex;

import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.diagnostic.timings.Timings;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.User;

import static de.jpx3.intave.share.Direction.Axis.*;

public final class v8Collider implements Collider {
  @Override
  public SimulationResult collide(
    User user, SimulationEnvironment environment, Motion offsetMotion,
    double positionX, double positionY, double positionZ, boolean inWeb
  ) {
	  if (inWeb) {
      offsetMotion.motionX *= 0.25D;
      offsetMotion.motionY *= 0.05f;
      offsetMotion.motionZ *= 0.25D;
    }
    Motion actualMotion = inWeb ? Motion.newEmpty() : offsetMotion.copy();

    double edgeSneakMotionX = offsetMotion.motionX;
    double edgeSneakMotionY = offsetMotion.motionY;
    double edgeSneakMotionZ = offsetMotion.motionZ;
    boolean step = false;
    boolean edgeSneak = false;
    double stepHeight = 0.0D;
    if (environment.onGround() && environment.isSneaking()) {
      BoundingBox boundingBox = environment.boundingBox();
      double size;
      for (size = 0.05D; offsetMotion.motionX != 0.0D && Collision.nonePresent(user, environment, boundingBox.offset(offsetMotion.motionX, -1.0D, 0.0D)); edgeSneakMotionX = offsetMotion.motionX) {
        if (offsetMotion.motionX < size && offsetMotion.motionX >= -size) {
          offsetMotion.motionX = 0.0D;
        } else if (offsetMotion.motionX > 0.0D) {
          offsetMotion.motionX -= size;
        } else {
          offsetMotion.motionX += size;
        }
        edgeSneak = true;
      }
      for (; offsetMotion.motionZ != 0.0D && Collision.nonePresent(user, environment, boundingBox.offset(0.0D, -1.0D, offsetMotion.motionZ)); edgeSneakMotionZ = offsetMotion.motionZ) {
        if (offsetMotion.motionZ < size && offsetMotion.motionZ >= -size) {
          offsetMotion.motionZ = 0.0D;
        } else if (offsetMotion.motionZ > 0.0D) {
          offsetMotion.motionZ -= size;
        } else {
          offsetMotion.motionZ += size;
        }
        edgeSneak = true;
      }
      for (; offsetMotion.motionX != 0.0D && offsetMotion.motionZ != 0.0D && Collision.nonePresent(user, environment, boundingBox.offset(offsetMotion.motionX, -1.0D, offsetMotion.motionZ)); edgeSneakMotionZ = offsetMotion.motionZ) {
        if (offsetMotion.motionX < size && offsetMotion.motionX >= -size) {
          offsetMotion.motionX = 0.0D;
        } else if (offsetMotion.motionX > 0.0D) {
          offsetMotion.motionX -= size;
        } else {
          offsetMotion.motionX += size;
        }
        edgeSneakMotionX = offsetMotion.motionX;
        if (offsetMotion.motionZ < size && offsetMotion.motionZ >= -size) {
          offsetMotion.motionZ = 0.0D;
        } else if (offsetMotion.motionZ > 0.0D) {
          offsetMotion.motionZ -= size;
        } else {
          offsetMotion.motionZ += size;
        }
        edgeSneak = true;
      }
    }
    Timings.CHECK_PHYSICS_SIMULATOR_BASE_COLLIDER_SHAPE_LOOKUP.start();
    BlockShape collisionShape = Collision.shape(
      user, environment, environment.boundingBox().expand(offsetMotion.motionX, offsetMotion.motionY, offsetMotion.motionZ)
    );
    Timings.CHECK_PHYSICS_SIMULATOR_BASE_COLLIDER_SHAPE_LOOKUP.stop();
    BoundingBox startBoundingBox = environment.boundingBox();
    BoundingBox entityBoundingBox = environment.boundingBox();
    offsetMotion.motionY = collisionShape.allowedOffset(Y_AXIS, entityBoundingBox, offsetMotion.motionY);
    entityBoundingBox = entityBoundingBox.offset(0.0D, offsetMotion.motionY, 0.0D);
    boolean flag1 = environment.onGround() || edgeSneakMotionY != offsetMotion.motionY && edgeSneakMotionY < 0.0D;
    offsetMotion.motionX = collisionShape.allowedOffset(X_AXIS, entityBoundingBox, offsetMotion.motionX);
    entityBoundingBox = entityBoundingBox.offset(offsetMotion.motionX, 0.0D, 0.0D);
    offsetMotion.motionZ = collisionShape.allowedOffset(Z_AXIS, entityBoundingBox, offsetMotion.motionZ);
    entityBoundingBox = entityBoundingBox.offset(0.0, 0.0, offsetMotion.motionZ);
    if (flag1 && (edgeSneakMotionX != offsetMotion.motionX || edgeSneakMotionZ != offsetMotion.motionZ)) {
      double copyX = offsetMotion.motionX;
      double copyY = offsetMotion.motionY;
      double copyZ = offsetMotion.motionZ;
      BoundingBox axisalignedbb3 = entityBoundingBox;
      entityBoundingBox = startBoundingBox;
      offsetMotion.motionY = environment.stepHeight();
      Timings.CHECK_PHYSICS_SIMULATOR_BASE_COLLIDER_SHAPE_LOOKUP.start();
      BlockShape shape = Collision.shape(user, environment, entityBoundingBox.expand(edgeSneakMotionX, offsetMotion.motionY, edgeSneakMotionZ));
      Timings.CHECK_PHYSICS_SIMULATOR_BASE_COLLIDER_SHAPE_LOOKUP.stop();
      BoundingBox axisalignedbb4 = entityBoundingBox;
      BoundingBox axisalignedbb5 = axisalignedbb4.expand(edgeSneakMotionX, 0.0D, edgeSneakMotionZ);
      double d9 = offsetMotion.motionY;
      d9 = shape.allowedOffset(Y_AXIS, axisalignedbb5, d9);
      axisalignedbb4 = axisalignedbb4.offset(0.0D, d9, 0.0D);
      double d15 = edgeSneakMotionX;
      d15 = shape.allowedOffset(X_AXIS, axisalignedbb4, d15);
      axisalignedbb4 = axisalignedbb4.offset(d15, 0.0D, 0.0D);
      double d16 = edgeSneakMotionZ;
      d16 = shape.allowedOffset(Z_AXIS, axisalignedbb4, d16);
      axisalignedbb4 = axisalignedbb4.offset(0.0D, 0.0D, d16);
      BoundingBox axisalignedbb14 = entityBoundingBox;
      double d17 = offsetMotion.motionY;
      d17 = shape.allowedOffset(Y_AXIS, axisalignedbb14, d17);
      axisalignedbb14 = axisalignedbb14.offset(0.0D, d17, 0.0D);
      double d18 = edgeSneakMotionX;
      d18 = shape.allowedOffset(X_AXIS, axisalignedbb14, d18);
      axisalignedbb14 = axisalignedbb14.offset(d18, 0.0D, 0.0D);
      double d19 = edgeSneakMotionZ;
      d19 = shape.allowedOffset(Z_AXIS, axisalignedbb14, d19);
      axisalignedbb14 = axisalignedbb14.offset(0.0D, 0.0D, d19);
      double d20 = d15 * d15 + d16 * d16;
      double d10 = d18 * d18 + d19 * d19;
      if (d20 > d10) {
        offsetMotion.motionX = d15;
        offsetMotion.motionZ = d16;
        offsetMotion.motionY = -d9;
        entityBoundingBox = axisalignedbb4;
      } else {
        offsetMotion.motionX = d18;
        offsetMotion.motionZ = d19;
        offsetMotion.motionY = -d17;
        entityBoundingBox = axisalignedbb14;
      }
      offsetMotion.motionY = shape.allowedOffset(Y_AXIS, entityBoundingBox, offsetMotion.motionY);
      entityBoundingBox = entityBoundingBox.offset(0.0, offsetMotion.motionY, 0.0);
      if (copyX * copyX + copyZ * copyZ >= offsetMotion.motionX * offsetMotion.motionX + offsetMotion.motionZ * offsetMotion.motionZ) {
        offsetMotion.motionX = copyX;
        offsetMotion.motionY = copyY;
        offsetMotion.motionZ = copyZ;
        entityBoundingBox = axisalignedbb3;
      } else {
        step = true;
        stepHeight = environment.stepHeight() + offsetMotion.motionY;
      }
    }
    boolean collidedVertically = edgeSneakMotionY != offsetMotion.motionY;
    boolean collidedHorizontally = edgeSneakMotionX != offsetMotion.motionX || edgeSneakMotionZ != offsetMotion.motionZ;
    boolean onGround = edgeSneakMotionY != offsetMotion.motionY && edgeSneakMotionY < 0.0;
    boolean moveResetX = edgeSneakMotionX != offsetMotion.motionX;
    boolean moveResetZ = edgeSneakMotionZ != offsetMotion.motionZ;
    double newPositionX = (entityBoundingBox.minX + entityBoundingBox.maxX) / 2.0D;
    double newPositionY = entityBoundingBox.minY;
    double newPositionZ = (entityBoundingBox.minZ + entityBoundingBox.maxZ) / 2.0D;
    offsetMotion.motionX = newPositionX - positionX;
    offsetMotion.motionY = newPositionY - positionY;
    offsetMotion.motionZ = newPositionZ - positionZ;
    if (moveResetX) {
      actualMotion.motionX = 0;
    }
    if (moveResetZ) {
      actualMotion.motionZ = 0;
    }
    return new SimulationResult(
      actualMotion,
      offsetMotion.copy(),
      null,
      onGround, collidedHorizontally, collidedVertically,
      moveResetX, moveResetZ, step, edgeSneak, stepHeight
    );
  }
}
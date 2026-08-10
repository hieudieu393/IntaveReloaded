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

package de.jpx3.intave.block.physics;

import com.google.common.collect.ImmutableList;
import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;
import org.bukkit.Material;

import java.util.List;

final class HoneyPhysics implements BlockPhysic {
  private Material honeyBlock;

  @Override
  public void setupFor(MinecraftVersion serverVersion) {
    honeyBlock = Material.getMaterial("HONEY_BLOCK");
  }

  @Override
  public Motion entityInside(User user, SimulationEnvironment environment, BlockPosition location, Position from, Motion motion, boolean insideBlockOrTooFast) {
    boolean blockEffectsAfterGravity = user.meta().protocol().newBlockEntityIntersectionLogic();
    double motionYBeforeGravity = blockEffectsAfterGravity
      ? getOldDeltaY(motion.motionY)
      : motion.motionY;
    if (doBlockPhysics(environment, location, motionYBeforeGravity)) {
      return updateMovement(
        environment,
        motion.motionX, motionYBeforeGravity, motion.motionZ,
        blockEffectsAfterGravity
      );
    }
    return null;
  }

  private static double getOldDeltaY(double deltaY) {
    return deltaY / 0.98F + 0.08D;
  }

  private static double getNewDeltaY(double deltaY) {
    return (deltaY - 0.08D) * 0.98F;
  }

  private boolean doBlockPhysics(
    SimulationEnvironment environment,
    BlockPosition blockPos, double motionY
  ) {
    if (environment.onGround()) {
      return false;
    } else if (environment.positionY() > blockPos.getY() + 0.9375D - 1.0E-7D) {
      return false;
    } else if (motionY >= -0.08D) {
      return false;
    } else {
      double d0 = Math.abs(blockPos.getX() + 0.5D - environment.positionX());
      double d1 = Math.abs(blockPos.getZ() + 0.5D - environment.positionZ());
      double d2 = 0.4375D + (double) (environment.width() / 2.0F);
      return d0 + 1.0E-7D > d2 || d1 + 1.0E-7D > d2;
    }
  }

  private Motion updateMovement(
    SimulationEnvironment environment,
    double motionX, double motionY, double motionZ,
    boolean blockEffectsAfterGravity
  ) {
    double throttledMotionY = blockEffectsAfterGravity
      ? getNewDeltaY(-0.05D)
      : -0.05D;
    Motion updatedMotion;
    if (motionY < -0.13D) {
      double d0 = -0.05D / motionY;
      updatedMotion = new Motion(motionX * d0, throttledMotionY, motionZ * d0);
    } else {
      updatedMotion = new Motion(motionX, throttledMotionY, motionZ);
    }
    environment.resetFallDistance();
    return updatedMotion;
  }

  @Override
  public boolean supportedOnServerVersion() {
    return honeyBlock != null;
  }

  @Override
  public List<Material> applicableMaterials() {
    return ImmutableList.of(honeyBlock);
  }
}

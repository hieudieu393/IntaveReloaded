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

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.Material;

import java.util.Collections;
import java.util.List;

import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.SLIME_BLOCK;

final class SlimePhysics implements BlockPhysic {
  private List<Material> material;

  @Override
  public void setupFor(MinecraftVersion serverVersion) {
    material = Collections.singletonList(Material.SLIME_BLOCK);
  }

  @Override
  public void fallenUpon(User user) {
    MovementMetadata movementData = user.meta().movement();
    if (!movementData.sneaking) {
      movementData.artificialFallDistance = 0;
    }
  }

  @Override
  // SlimeBlock.bounceUp
  public Motion landed(
    User user,
    SimulationEnvironment environment,
    double motionX, double motionY, double motionZ
  ) {
    if (motionY < 0.0 && !environment.isSneaking()) {
      return new Motion(motionX, -motionY, motionZ);
    } else {
      return null;
    }
  }

  @Override
  // SlimeBlock.stepOn
  public Motion stepOn(
    User user, SimulationEnvironment environment,
    double motionX, double motionY, double motionZ
  ) {
    if (Math.abs(motionY) < 0.1D && !environment.isSneaking()) {
      double d0 = 0.4D + Math.abs(motionY) * 0.2D;
      motionX *= d0;
      motionZ *= d0;
      environment.activeTick(SLIME_BLOCK);
      return new Motion(motionX, motionY, motionZ);
    } else {
      return null;
    }
  }

  @Override
  public List<Material> applicableMaterials() {
    return material;
  }
}
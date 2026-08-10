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
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.collision.modifier.PowderSnowCollisionModifier;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.block.type.MaterialSearch;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;
import org.bukkit.Material;
import org.bukkit.util.Vector;

import java.util.Set;

final class PowderSnowPhysics implements BlockPhysic {
  private Set<Material> materials;
  private boolean supported;

  @Override
  public void setupFor(MinecraftVersion serverVersion) {
    materials = MaterialSearch.materialsThatContain("POWDER_SNOW");
    supported = !materials.isEmpty();
  }

  @Override
  public Motion entityInside(User user, SimulationEnvironment environment, BlockPosition location, Position from, Motion motion, boolean insideBlockOrTooFast) {
    Material block = VolatileBlockAccess.typeAccess(user, environment.blockPosition());
    if (materials.contains(block)) {
      environment.setMotionMultiplier(new Vector(0.9f, 1.5f, 0.9f));
    }
    return null;
  }

  @Override
  public BlockShape entityInsideCollisionShape(
    User user,
    SimulationEnvironment environment,
    BlockPosition position
  ) {
    if (!user.meta().protocol().powderSnowInsideShapeUsesCollisionContext()) {
      return BlockShapes.cubeAt(position.getBlockX(), position.getBlockY(), position.getBlockZ());
    }
    BlockShape collisionShape = PowderSnowCollisionModifier.collisionShape(
      user, environment, position.getBlockX(), position.getBlockY(), position.getBlockZ()
    );
    return collisionShape.isEmpty()
      ? BlockShapes.cubeAt(position.getBlockX(), position.getBlockY(), position.getBlockZ())
      : collisionShape;
  }

  @Override
  public boolean supportedOnServerVersion() {
    return supported;
  }

  @Override
  public Set<Material> applicableMaterials() {
    return materials;
  }
}

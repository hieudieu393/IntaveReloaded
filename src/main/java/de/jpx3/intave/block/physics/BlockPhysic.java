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
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;
import org.bukkit.Material;

import java.util.Collection;

public interface BlockPhysic {
  void setupFor(MinecraftVersion serverVersion);

  // Called from #doBlockCollisions
  default @Nullable Motion entityInside(
    User user, SimulationEnvironment environment,
    BlockPosition location, Position from,
    Motion motion, boolean insideBlockOrTooFast
  ) {
    return null;
  }

  default BlockShape entityInsideCollisionShape(
    User user,
    SimulationEnvironment environment,
    BlockPosition position
  ) {
    return BlockShapes.cubeAt(position.getBlockX(), position.getBlockY(), position.getBlockZ());
  }

  default @Nullable Motion stepOn(User user, SimulationEnvironment environment, double motionX, double motionY, double motionZ) {
    return null;
  }

  default @Nullable Motion landed(User user, SimulationEnvironment environment, double motionX, double motionY, double motionZ) {
    return null;
  }

  default void fallenUpon(User user) {
  }

  default boolean supportedOnServerVersion() {
    return true;
  }

  Collection<Material> applicableMaterials();
}

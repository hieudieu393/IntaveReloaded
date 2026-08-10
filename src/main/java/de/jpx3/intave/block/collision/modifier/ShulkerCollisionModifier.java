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

package de.jpx3.intave.block.collision.modifier;

import de.jpx3.intave.block.collision.CollisionOrigin;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.block.tick.ShulkerBox;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.user.User;
import org.bukkit.Material;

import static de.jpx3.intave.block.collision.CollisionOrigin.INTERSECTION_CHECK;

final class ShulkerCollisionModifier extends CollisionModifier {
  @Override
  public BlockShape modify(
    User user, SimulationEnvironment environment, BoundingBox userBox,
    int posX, int posY, int posZ, BlockShape shape, CollisionOrigin collisionType
  ) {
    if (collisionType == INTERSECTION_CHECK) {
      return BlockShapes.emptyShape();
    }
    ShulkerBox shulker = environment.shulkerBoxAt(posX, posY, posZ);
    return shulker != null ? shulker.originShape().contextualized(posX, posY, posZ) : shape;
  }

  @Override
  public boolean matches(Material material) {
    return material.name().contains("SHULKER_BOX");
  }
}

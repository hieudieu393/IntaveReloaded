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
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.user.User;
import org.bukkit.Material;

import static de.jpx3.intave.block.collision.CollisionOrigin.INTERSECTION_CHECK;

final class PistonCollisionModifier extends CollisionModifier {
  @Override
  public BlockShape modify(
    User user, SimulationEnvironment environment, BoundingBox userBox,
    int posX, int posY, int posZ, BlockShape shape, CollisionOrigin type
  ) {
    // Lets just ignore the piston box if a piston is expanding
    if (environment.pistonMotionToleranceRemaining() > 0 && type == INTERSECTION_CHECK) {
      return BlockShapes.emptyShape();
    }
//    Set<Motion> toleratedPistonMotions = movement.toleratedPistonMotions;
//    if (!toleratedPistonMotions.isEmpty() && type == INTERSECTION_CHECK) {
//      return BlockShapes.emptyShape();
//    }
    return shape;
  }

  @Override
  public boolean matches(Material material) {
    return material.name().contains("PISTON");
  }
}

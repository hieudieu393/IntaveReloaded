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

package de.jpx3.intave.block.inside;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.check.movement.physics.environment.MockSimulationEnvironment;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BlockInsideShapeTest {
  private static final ExposedBlockInsideCheck CHECK = new ExposedBlockInsideCheck();

  @Test
  void skipsANonFullInsideShapeOutsideTheSweptPlayerBox() {
    User user = UserFactory.createFallback();
    MockSimulationEnvironment environment = new MockSimulationEnvironment();
    BlockShape lowShape = BoundingBox.fromBounds(0.0D, 0.0D, 0.0D, 1.0D, 0.9D, 1.0D);
    Position aboveShape = Position.of(0.5D, 1.0D, 0.5D);

    assertFalse(CHECK.intersects(user, environment, aboveShape, aboveShape, lowShape));
  }

  @Test
  void appliesANonFullInsideShapeIntersectedByThePlayerBox() {
    User user = UserFactory.createFallback();
    MockSimulationEnvironment environment = new MockSimulationEnvironment();
    BlockShape lowShape = BoundingBox.fromBounds(0.0D, 0.0D, 0.0D, 1.0D, 0.9D, 1.0D);
    Position intersectingShape = Position.of(0.5D, 0.8D, 0.5D);

    assertTrue(CHECK.intersects(user, environment, intersectingShape, intersectingShape, lowShape));
  }

  @Test
  void fullBlockInsideShapeUsesTheClientFastPath() {
    User user = UserFactory.createFallback();
    MockSimulationEnvironment environment = new MockSimulationEnvironment();
    Position origin = Position.of(0.0D, 0.0D, 0.0D);

    assertTrue(CHECK.intersects(user, environment, origin, origin, BlockShapes.cubeAt(10, 10, 10)));
  }

  private static final class ExposedBlockInsideCheck extends BlockInsideCheck {
    @Override
    public void checkInsideBlocks(
      User user,
      SimulationEnvironment environment,
      Motion motion,
      List<EntityMovement> movements
    ) {
    }

    private boolean intersects(
      User user,
      SimulationEnvironment environment,
      Position from,
      Position to,
      BlockShape shape
    ) {
      return intersectsEntityInsideShape(user, environment, from, to, shape);
    }
  }
}

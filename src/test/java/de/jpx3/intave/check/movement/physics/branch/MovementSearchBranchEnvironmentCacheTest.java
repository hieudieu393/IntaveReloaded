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

package de.jpx3.intave.check.movement.physics.branch;

import de.jpx3.intave.check.movement.physics.environment.MockSimulationEnvironment;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.share.Rotation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

final class MovementSearchBranchEnvironmentCacheTest {
  @Test
  void configurationVariantsShareOneModifiedImmutableEnvironment() {
    MockSimulationEnvironment environment = new MockSimulationEnvironment();
    MovementSearchInput input = MovementSearchInput.forTick(
      null, null, environment.immutableView(), false
    );
    MovementSearchBranch rotated = MovementSearchBranch.blank(input)
      .withRotation(Rotation.of(90.0F, 30.0F));
    MovementSearchBranch configurationVariant = rotated.withHandActive(true);

    SimulationEnvironment first = rotated.modifiedImmutableView(input);
    SimulationEnvironment second = configurationVariant.modifiedImmutableView(input);

    assertSame(first, second);
    assertEquals(90.0F, first.rotationYaw());
    assertEquals(30.0F, first.rotationPitch());
    assertEquals(2, first.depth());
  }
}

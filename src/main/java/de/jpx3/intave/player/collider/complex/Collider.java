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

import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;

public interface Collider {
  SimulationResult collide(
    User user,
    SimulationEnvironment environment,
    Motion motion,
    double positionX, double positionY, double positionZ,
    boolean inWeb
  );

  default SimulationResult[] collideMany(
	  User user, SimulationEnvironment environment,
	  Motion[] motions, Position position, boolean inWeb
  ) {
    SimulationResult[] results = new SimulationResult[motions.length];
    for (int index = 0; index < motions.length; index++) {
      results[index] = collide(user, environment, motions[index], position.getX(), position.getY(), position.getZ(), inWeb);
    }
    return results;
  }
}
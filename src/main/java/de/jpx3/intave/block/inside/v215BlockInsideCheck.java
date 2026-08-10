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

import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;
import it.unimi.dsi.fastutil.longs.LongSet;

public class v215BlockInsideCheck extends v214BlockInsideCheck {
	@Override
	protected void checkMovement(
		User user,
		SimulationEnvironment environment,
		Motion motion,
		EntityMovement movement,
		LongSet visitedBlocks,
		BoundingBox finalBox
	) {
		Motion actualMovement = movement.to().subtractToMotion(movement.from());
		if (!movement.axisDependentOriginalMovement().isPresent() || actualMovement.lengthSquared() == 0.0) {
			super.checkMovement(user, environment, motion, movement, visitedBlocks, finalBox);
			return;
		}

		Position from = movement.from();
		for (Direction.Axis axis : Direction.axisStepOrder(actualMovement)) {
			double partial = actualMovement.partialMotionIn(axis);
			if (partial != 0.0) {
				Position to = from.relative(axis.positive(), partial);
				checkSegment(
					user, environment, motion, from, to, visitedBlocks,
					boxForMovement(user, environment, to, finalBox)
				);
				from = to;
			}
		}
	}
}

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
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;

import java.util.List;

import static de.jpx3.intave.share.ClientMath.floor;

/** The final-bounding-box check used by 1.21 and 1.21.1. */
public class v21BlockInsideCheck extends BlockInsideCheck {
	@Override
	public void checkInsideBlocks(
		User user,
		SimulationEnvironment environment,
		Motion motion,
		List<EntityMovement> movements
	) {
		BoundingBox box = BoundingBox.fromPosition(user, environment, environment.position());
		double epsilon = 1.0E-7D;
		int minX = floor(box.minX + epsilon);
		int minY = floor(box.minY + epsilon);
		int minZ = floor(box.minZ + epsilon);
		int maxX = floor(box.maxX - epsilon);
		int maxY = floor(box.maxY - epsilon);
		int maxZ = floor(box.maxZ - epsilon);
		Position movementFrom = movements.isEmpty()
			? environment.position()
			: movements.get(movements.size() - 1).to();

		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				for (int z = minZ; z <= maxZ; z++) {
					applyBlockEffect(
						user, environment, motion,
						new BlockPosition(x, y, z), movementFrom, true
					);
				}
			}
		}
	}
}

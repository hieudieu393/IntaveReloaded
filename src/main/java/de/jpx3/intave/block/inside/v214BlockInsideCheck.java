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
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;

public class v214BlockInsideCheck extends v212BlockInsideCheck {
	@Override
	protected BoundingBox boxForMovement(
		User user,
		SimulationEnvironment environment,
		Position to,
		BoundingBox finalBox
	) {
		return BoundingBox.fromPosition(user, environment, to).shrink(1.0E-5F);
	}

	@Override
	protected boolean usesTravelEpsilon() {
		return false;
	}
}

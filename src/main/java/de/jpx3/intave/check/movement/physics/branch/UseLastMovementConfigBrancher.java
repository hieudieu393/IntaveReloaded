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

import de.jpx3.intave.check.movement.physics.config.MovementConfiguration;

import java.util.Collection;

public final class UseLastMovementConfigBrancher extends MovementSearchBrancher {
	@Override
	public void branch(
		MovementSearchInput input,
		MovementSearchBranch inputBranch,
		Collection<MovementSearchBranch> outputBranches
	) {
		MovementConfiguration previous = input.environment().lastMovementConfiguration();
		outputBranches.add(inputBranch.withLastMovementConfiguration(previous));
	}
}

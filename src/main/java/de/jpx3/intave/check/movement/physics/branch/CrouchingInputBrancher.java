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

import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.user.meta.ProtocolMetadata;

import java.util.Collection;

final class CrouchingInputBrancher extends MovementSearchBrancher {
	@Override
	public void branch(
		MovementSearchInput input,
		MovementSearchBranch inputBranch,
		Collection<MovementSearchBranch> outputBranches
	) {
		SimulationEnvironment environment = input.environment();
		ProtocolMetadata protocol = input.user().meta().protocol();
    if (!protocol.trailsAndTailsUpdate()) {
			outputBranches.add(inputBranch);
			return;
    }
    boolean ambiguousRelease = protocol.beeUpdate()
      && environment.lastSneaking()
      && !environment.isSneaking();
    if (!ambiguousRelease) {
      outputBranches.add(inputBranch);
      return;
    }

    outputBranches.add(inputBranch);
		outputBranches.add(inputBranch.withoutCrouchingInputSlowdown());
	}
}

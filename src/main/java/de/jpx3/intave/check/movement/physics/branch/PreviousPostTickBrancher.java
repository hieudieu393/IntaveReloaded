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

import de.jpx3.intave.check.movement.physics.environment.PostTickSimulation;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;

import java.util.Collection;
import java.util.List;

// Last Post-Tick Motion
public final class PreviousPostTickBrancher extends MovementSearchBrancher {
	@Override
	public void branch(
		MovementSearchInput input,
		MovementSearchBranch inputBranch,
		Collection<MovementSearchBranch> outputBranches
	) {
		SimulationEnvironment environment = input.environment();
		List<PostTickSimulation> candidates = environment.postTickMotionCandidates();
		if (candidates.isEmpty()) {
			outputBranches.add(inputBranch);
			return;
		}
		if (candidates.size() == 1) {
			PostTickSimulation candidate = candidates.get(0);
			outputBranches.add(withCandidate(inputBranch, candidate));
			return;
		}
		for (PostTickSimulation candidate : candidates) {
			outputBranches.add(withCandidate(inputBranch, candidate));
		}
	}

	private static MovementSearchBranch withCandidate(
		MovementSearchBranch inputBranch,
		PostTickSimulation candidate
	) {
		return inputBranch.withPreviousPostTickCandidate(
			candidate.motion(), candidate.priorSprinting()
		);
	}
}

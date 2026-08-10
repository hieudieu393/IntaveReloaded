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

import java.util.Collection;

/*
 * The following can happen:
 * A player rotates, then sends a flying packet, then rotate-moves.
 * We must account for the flying-packet using the outdated rotation.
 * This class was used instead of tick-ambiguous updates, since it does basically the same.
 */
public final class RotationBrancher extends MovementSearchBrancher {
	@Override
	public void branch(MovementSearchInput input, MovementSearchBranch inputBranch, Collection<MovementSearchBranch> outputBranches) {
		SimulationEnvironment environment = input.environment();
		if (environment.lastRotationEqualsRotation()) {
			outputBranches.add(inputBranch);
			return;
		}
		if (input.user().meta().protocol().flyingPacketUncertaintyRadius() < 0.00001) {
			outputBranches.add(inputBranch);
			return;
		}
		outputBranches.add(inputBranch);
		outputBranches.add(inputBranch.withRotation(environment.lastRotation()).setToOnlyUsableForImplicitFlyingPackets());
	}
}

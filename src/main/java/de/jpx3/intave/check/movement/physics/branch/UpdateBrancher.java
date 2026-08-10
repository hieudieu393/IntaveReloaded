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
import de.jpx3.intave.check.movement.physics.update.CausalConstraint;
import de.jpx3.intave.check.movement.physics.update.TickAmbiguousUpdate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;

public final class UpdateBrancher extends MovementSearchBrancher {
	@Override
	public void branch(MovementSearchInput input, MovementSearchBranch inputBranch, Collection<MovementSearchBranch> outputBranches) {
		SimulationEnvironment environment = input.environment();

		List<TickAmbiguousUpdate> updates = environment.possibleTickAmbiguousUpdates();
		if (updates.isEmpty()) {
			outputBranches.add(inputBranch);
			return;
		}
		// sort by sequenceKey
		Collections.sort(updates);

		// Some updates MUST happen in this tick, so we enforce them and all before to happen now
		long lastVerifiedCompleteUpdate = Long.MIN_VALUE;
		for (TickAmbiguousUpdate update : updates) {
			CausalConstraint updateConstraint = update.constraint();
			if (updateConstraint.mustHappenThisTick(environment) && !updateConstraint.isOpenBounded()) {
				lastVerifiedCompleteUpdate = Math.max(lastVerifiedCompleteUpdate, updateConstraint.sequenceNumber());
			}
		}

		UnaryOperator<SimulationEnvironment> environmentUpdater = UnaryOperator.identity();
		List<UnaryOperator<SimulationEnvironment>> options = new ArrayList<>();
		List<Boolean> canFinishTick = new ArrayList<>();
//		List<String> optionDebug = new ArrayList<>();

		/*
		 *   UUUU (T) UUUL T IUUU
		 *                 ^
		 *   all branches that did not complete all previous Us must be denied.
		 *   however, since we might have further flying-tick injection simulation,
		 *   an invalid branch may not be detectable unless after sufficient context is given.
		 *
		 *   special case L/I:
		 *   for the special case that an action is still open-bounded in its tick-end constraint,
		 *   we postpone the fulfillment requirement until the bound is closed.
		 *
		 */

		for (TickAmbiguousUpdate update : updates) {
			// only allow non-action if the update is not required to happen now
			CausalConstraint constraint = update.constraint();
			boolean postponeAllowed = constraint.sequenceNumber() > lastVerifiedCompleteUpdate;
			boolean completeAllowed = constraint.sequenceNumber() == lastVerifiedCompleteUpdate || constraint.isOpenBounded();

			options.add(environmentUpdater);
			canFinishTick.add(postponeAllowed);
//			optionDebug.add("Postpone " + update);
			environmentUpdater = andThen(environmentUpdater, env -> {
				update.applyTo(env);
				env.setActiveSequence(constraint.sequenceNumber());
				return env;
			});
			options.add(environmentUpdater);
			canFinishTick.add(completeAllowed);
//			optionDebug.add("Complete " + update);
		}

		for (int i = options.size() - 1; i >= 0; i--) {
			UnaryOperator<SimulationEnvironment> envUpdate = options.get(i);
			boolean thisCanFinishTick = canFinishTick.get(i);
			MovementSearchBranch cfg = inputBranch;
			cfg = cfg.withAmbiguousUpdates(envUpdate, i, thisCanFinishTick);
			cfg = cfg.withExplicitTickFinishAllow(thisCanFinishTick);
			outputBranches.add(cfg);
//			input.user().sendMessage("Branching option: " + optionDebug.get(i) + " (canFinishTick=" + thisCanFinishTick + ")");
		}
//		input.user().sendMessage("Branching " + options.size() + " options");
	}

	private static <T> UnaryOperator<T> andThen(UnaryOperator<T> first, UnaryOperator<T> second) {
		return t -> second.apply(first.apply(t));
	}
}

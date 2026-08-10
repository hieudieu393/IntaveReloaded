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

package de.jpx3.intave.check.movement.physics.search;

import de.jpx3.intave.check.movement.physics.environment.PostTickSimulation;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.check.movement.physics.evaluation.EvaluationTag;
import de.jpx3.intave.check.movement.physics.evaluation.SimulationEvaluator;
import de.jpx3.intave.check.movement.physics.simulator.Simulation;
import de.jpx3.intave.check.movement.physics.simulator.Simulator;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RollbackSimulationSearch implements SimulationSearch {
	private final SimulationSearch delegate;
	private final SimulationEvaluator evaluator;

	private RollbackSimulationSearch(SimulationSearch delegate, SimulationEvaluator evaluator) {
		this.delegate = delegate;
		this.evaluator = evaluator;
	}

	@Override
	public Simulation tickSearch(
		User user, SimulationEnvironment currentEnvironment,
		Simulator simulator, SimulationSearchOptions options
	) {
		Simulation firstSimulation = delegate.tickSearch(user, currentEnvironment, simulator, options);
		MovementMetadata movement = user.meta().movement();
		SimulationEnvironment rollbackEnvironment = movement.beforePreviousTickEnvironment;

		if (firstSimulation.offsetDifference() < 0.0005 || evaluate(user, firstSimulation) < 0.01 || rollbackEnvironment == null) {
			storeRollbackEnvironment(movement, firstSimulation);
			return firstSimulation;
		}

		Set<Simulation> simulations = delegate.exhaustiveTickSearch(user, rollbackEnvironment, simulator);
		if (simulations.isEmpty()) {
			user.sendMessage("Exhaustive did not yield any simulations");
			storeRollbackEnvironment(movement, firstSimulation);
			return firstSimulation;
		}

		if (simulations.size() == 1) {
			user.sendMessage("Exhaustive yielded 1 simulation, skipping search. " + simulations.iterator().next().actualMotion());
			storeRollbackEnvironment(movement, firstSimulation);
			return firstSimulation;
		}

		user.sendMessage("Exhaustive yielded " + simulations.size() + " simulations, searching for best rollback simulation");

		Simulation bestSimulation = firstSimulation;
		Set<Motion> bestOffsetMotions = new HashSet<>();
		for (Simulation simulation : simulations) {
			if (!simulation.offsetMotionDiffersFromActualMotion()) {
				continue;
			}
			SimulationEnvironment firstTickEnvironment = simulation.environment().mutableView();
			Simulator nextSimulator = simulator.simulateAround(
				user, firstTickEnvironment, simulation,
				currentEnvironment.position(), currentEnvironment.rotation()
			);
			Motion outputMotion = firstTickEnvironment.mutableBaseMotionCopy();
			if (bestOffsetMotions.contains(outputMotion)) {
				continue;
			}
			bestOffsetMotions.add(outputMotion);
			Simulation nextSimulation = delegate.tickSearch(
				user, firstTickEnvironment, nextSimulator, options
			);
			bestSimulation = nextSimulation.select(bestSimulation);
			if (bestSimulation.offsetDifference() < 0.0005) {
				storeRollbackEnvironment(movement, bestSimulation);
				return bestSimulation;
			}
		}
		if (!bestOffsetMotions.isEmpty()) {
			user.player().sendMessage("Rollbacked ("+bestOffsetMotions.size()+") simulation search: " + bestSimulation.offsetDifference() + " vs " + firstSimulation.offsetDifference());
		}
		if (bestOffsetMotions.size() == 1) {
			storeRollbackEnvironment(movement, firstSimulation);
			return firstSimulation;
		}
		storeRollbackEnvironment(movement, bestSimulation);
		return bestSimulation;
	}

	@Override
	public Set<Simulation> exhaustiveTickSearch(User user, SimulationEnvironment environment, Simulator simulator) {
		return delegate.exhaustiveTickSearch(user, environment, simulator);
	}

	@Override
	public List<PostTickSimulation> afterTickMotionCandidates(User user, SimulationEnvironment environment, Simulator simulator, Position newPosition, PostTickMotionType motionType) {
		return delegate.afterTickMotionCandidates(user, environment, simulator, newPosition, motionType);
	}

	private void storeRollbackEnvironment(MovementMetadata movement, Simulation simulation) {
		movement.beforePreviousTickEnvironment = simulation.environment().immutableCopy();
	}

	private double evaluate(
		User user, Simulation simulation
	) {
		Motion offsetMotion = simulation.offsetMotion();
		Set<EvaluationTag> unusedEvalTags = new HashSet<>();
		double horizontalVL = evaluator.calculateHorizontalViolationIncrease(
			user, simulation.environment(), offsetMotion.motionX, offsetMotion.motionZ,
			false, false, unusedEvalTags
		);
		double verticalVL = evaluator.calculateVerticalViolationIncrease(
			user, simulation.environment(), offsetMotion.motionY,
			false, false, unusedEvalTags
		);
		return horizontalVL + verticalVL;
	}

	public static RollbackSimulationSearch of(SimulationSearch delegate, SimulationEvaluator evaluator) {
		return new RollbackSimulationSearch(delegate, evaluator);
	}
}

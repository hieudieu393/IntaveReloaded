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

final class AttackReduceBrancher extends MovementSearchBrancher {
  private static final boolean[] PESSIMISTIC = new boolean[]{false, true};
  private static final boolean[] NEVER = new boolean[]{false};

  @Override
  public void branch(MovementSearchInput input, MovementSearchBranch inputBranch, Collection<MovementSearchBranch> outputBranches) {
    SimulationEnvironment environment = inputBranch.modifiedImmutableView(input);
    int reduceTicks = environment.reduceTicks();
    if (reduceTicks == 0) {
      outputBranches.add(inputBranch.withReduceTicks(0).withReduceBefore(false));
      return;
    }
    for (int reduceIndex = 0; reduceIndex <= Math.min(reduceTicks, 3); reduceIndex++) {
      for (boolean reduceBefore : reduceIndex > 0 ? PESSIMISTIC : NEVER) {
        outputBranches.add(inputBranch.withReduceTicks(reduceIndex).withReduceBefore(reduceBefore));
      }
    }
  }
}

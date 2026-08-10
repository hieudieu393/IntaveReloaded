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

import de.jpx3.intave.check.movement.physics.branch.MovementSearchBranch;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class ThreeTickBranchFrequencyTest {
  @Test
  void fingerprintsIncludeDecisionTypeValueAndOrder() {
    MovementSearchBranch blank = MovementSearchBranch.blank(null);
    MovementSearchBranch jumped = blank.withKeypress(1, 0).withJumped(true);

    assertNotEquals(jumped.frequencyKey(), blank.withKeypress(1, 0).withJumped(false).frequencyKey());
    assertNotEquals(jumped.frequencyKey(), blank.withPredictedKeypress(1, 0).withJumped(true).frequencyKey());
    assertNotEquals(jumped.frequencyKey(), blank.withJumped(true).withKeypress(1, 0).frequencyKey());
  }

  private static MovementSearchBranch branch(int forward, int strafe) {
    return MovementSearchBranch.blank(null).withKeypress(forward, strafe);
  }
}

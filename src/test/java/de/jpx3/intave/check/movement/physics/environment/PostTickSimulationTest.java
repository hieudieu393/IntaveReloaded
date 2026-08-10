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

package de.jpx3.intave.check.movement.physics.environment;

import de.jpx3.intave.share.Motion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class PostTickSimulationTest {
	@Test
	void motionAndPriorSprintFormCandidateIdentity() {
		Motion motion = new Motion(0.1, 0.2, 0.3);
		PostTickSimulation walking = new PostTickSimulation(motion, false);
		PostTickSimulation sprinting = new PostTickSimulation(motion, true);

		assertFalse(walking.sameAs(sprinting));
		assertTrue(walking.sameAs(new PostTickSimulation(motion, false)));
	}

	@Test
	void motionIsDefensivelyCopied() {
		Motion source = new Motion(0.1, 0.2, 0.3);
		PostTickSimulation candidate = new PostTickSimulation(source, true);
		source.motionX = 4.0;

		Motion returned = candidate.motion();
		returned.motionY = 5.0;

		assertEquals(0.1, candidate.motion().motionX(), 0.0);
		assertEquals(0.2, candidate.motion().motionY(), 0.0);
		assertTrue(candidate.priorSprinting());
	}
}

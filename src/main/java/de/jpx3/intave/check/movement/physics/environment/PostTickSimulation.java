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

public final class PostTickSimulation {
	private final Motion motion;
	private final boolean priorSprinting;

	public PostTickSimulation(Motion motion, boolean priorSprinting) {
		this.motion = motion.copy();
		this.priorSprinting = priorSprinting;
	}

	public Motion motion() {
		return motion.copy();
	}

	public boolean priorSprinting() {
		return priorSprinting;
	}

	public PostTickSimulation withMotion(Motion motion) {
		return new PostTickSimulation(motion, priorSprinting);
	}

	public PostTickSimulation copy() {
		return new PostTickSimulation(motion, priorSprinting);
	}

	public boolean sameAs(PostTickSimulation other) {
		return priorSprinting == other.priorSprinting && motion.equals(other.motion);
	}

	@Override
	public String toString() {
		return motion + (priorSprinting ? " _SPR" : "");
	}
}

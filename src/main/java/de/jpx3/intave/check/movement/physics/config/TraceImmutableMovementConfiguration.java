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

package de.jpx3.intave.check.movement.physics.config;

public final class TraceImmutableMovementConfiguration implements MovementConfiguration {
	private final MovementConfiguration delegate;

	private boolean requiredJumpingState;
	private boolean requiredReducingState;
	private boolean requiredSprintingState;
	private boolean requiredActualMotionOverride;
	private boolean requiredBlockInsideCheckVersion;

	public TraceImmutableMovementConfiguration(MovementConfiguration delegate) {
		this.delegate = delegate;
	}

	@Override
	public int forward() {
		return delegate.forward();
	}

	@Override
	public int strafe() {
		return delegate.strafe();
	}

	@Override
	public boolean isHandActive() {
		return delegate.isHandActive();
	}

	@Override
	public boolean reduceBefore() {
		return delegate.reduceBefore();
	}

	@Override
	public int reduceTicks() {
		return delegate.reduceTicks();
	}

	@Override
	public boolean isJumping() {
		requiredJumpingState = true;
		return delegate.isJumping();
	}

	@Override
	public boolean overrideEndMotionToActualMotion() {
		requiredActualMotionOverride = true;
		return delegate.overrideEndMotionToActualMotion();
	}

	@Override
	public boolean usesAlternateBlockInsideCheck() {
		requiredBlockInsideCheckVersion = true;
		return delegate.usesAlternateBlockInsideCheck();
	}

	@Override
	public boolean isReducing() {
		requiredReducingState = true;
		return delegate.isReducing();
	}

	@Override
	public boolean isSprinting() {
		requiredSprintingState = true;
		return delegate.isSprinting();
	}

	@Override
	public MovementConfiguration pressingA() {
		throw new UnsupportedOperationException("Cannot modify a recording configuration");
	}

	@Override
	public MovementConfiguration pressingD() {
		throw new UnsupportedOperationException("Cannot modify a recording configuration");
	}

	@Override
	public MovementConfiguration pressingS() {
		throw new UnsupportedOperationException("Cannot modify a recording configuration");
	}

	@Override
	public MovementConfiguration pressingW() {
		throw new UnsupportedOperationException("Cannot modify a recording configuration");
	}

	@Override
	public MovementConfiguration withActiveHand() {
		throw new UnsupportedOperationException("Cannot modify a recording configuration");
	}

	@Override
	public MovementConfiguration withForward(int forward) {
		throw new UnsupportedOperationException("Cannot modify a recording configuration");
	}

	@Override
	public MovementConfiguration withHandActive(boolean hasHandActive) {
		throw new UnsupportedOperationException("Cannot modify a recording configuration");
	}

	@Override
	public MovementConfiguration withJump() {
		throw new UnsupportedOperationException("Cannot modify a recording configuration");
	}

	@Override
	public MovementConfiguration withJumped(boolean hasJumped) {
		throw new UnsupportedOperationException("Cannot modify a recording configuration");
	}

	@Override
	public MovementConfiguration withKeypress(int forward, int strafe) {
		throw new UnsupportedOperationException("Cannot modify a recording configuration");
	}

	@Override
	public MovementConfiguration withReduceBefore(boolean hasReduceBefore) {
		throw new UnsupportedOperationException("Cannot modify a recording configuration");
	}

	@Override
	public MovementConfiguration withReduceTicks(int ticks) {
		throw new UnsupportedOperationException("Cannot modify a recording configuration");
	}

	@Override
	public MovementConfiguration withSprinting() {
		throw new UnsupportedOperationException("Cannot modify a recording configuration");
	}

	@Override
	public MovementConfiguration withSprintingSetTo(boolean sprinting) {
		throw new UnsupportedOperationException("Cannot modify a recording configuration");
	}

	@Override
	public MovementConfiguration withStrafe(int strafe) {
		throw new UnsupportedOperationException("Cannot modify a recording configuration");
	}

	@Override
	public MovementConfiguration withoutActiveHand() {
		throw new UnsupportedOperationException("Cannot modify a recording configuration");
	}

	@Override
	public MovementConfiguration withoutJump() {
		throw new UnsupportedOperationException("Cannot modify a recording configuration");
	}

	@Override
	public MovementConfiguration withoutKeypress() {
		throw new UnsupportedOperationException("Cannot modify a recording configuration");
	}

	@Override
	public MovementConfiguration withoutReducing() {
		throw new UnsupportedOperationException("Cannot modify a recording configuration");
	}

	@Override
	public MovementConfiguration withoutSprinting() {
		throw new UnsupportedOperationException("Cannot modify a recording configuration");
	}

	@Override
	public MovementConfiguration allowOverrideToActualMotion() {
		throw new UnsupportedOperationException("Cannot modify a recording configuration");
	}

	@Override
	public MovementConfiguration denyOverrideToActualMotion() {
		throw new UnsupportedOperationException("Cannot modify a recording configuration");
	}

	@Override
	public MovementConfiguration withAlternativeBlockInsideCheck() {
		throw new UnsupportedOperationException("Cannot modify a recording configuration");
	}

	@Override
	public MovementConfiguration withoutAlternativeBlockInsideCheck() {
		throw new UnsupportedOperationException("Cannot modify a recording configuration");
	}

	public boolean requiredSprintingState() {
		return requiredSprintingState;
	}

	public boolean requiredJumpingState() {
		return requiredJumpingState;
	}

	public boolean requiredReducingState() {
		return requiredReducingState;
	}

	public boolean requiredActualMotionOverride() {
		return requiredActualMotionOverride;
	}

	public boolean requiredBlockInsideCheckVersion() {
		return requiredBlockInsideCheckVersion;
	}

	public boolean requiredAnyState() {
		return requiredJumpingState || requiredReducingState || requiredSprintingState
			|| requiredActualMotionOverride || requiredBlockInsideCheckVersion;
	}

	public void reset() {
		requiredJumpingState = false;
		requiredReducingState = false;
		requiredSprintingState = false;
		requiredActualMotionOverride = false;
		requiredBlockInsideCheckVersion = false;
	}

	@Override
	public TraceImmutableMovementConfiguration withRecording() {
		return this;
	}
}

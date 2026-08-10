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

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class IndexBasedMovementConfiguration implements MovementConfiguration {
  private static final List<State> states;

  private static final QuadState forward = new QuadState();
  private static final QuadState strafe = new QuadState();

  private static final QuadState attackReduceTicks = new QuadState();
  private static final BiState sprintingState = new BiState();
  private static final BiState jumped = new BiState();
  private static final BiState handActive = new BiState();
  private static final BiState reduceBefore = new BiState();
  private static final BiState noHorizontalMotionReset = new BiState();
  private static final BiState alternativeBlockInsideCheck = new BiState();

  static {
    List<State> statez = new ArrayList<>();
    statez.add(forward);
    statez.add(strafe);
    statez.add(attackReduceTicks);
    statez.add(sprintingState);
    statez.add(jumped);
    statez.add(handActive);
    statez.add(reduceBefore);
    statez.add(noHorizontalMotionReset);
    statez.add(alternativeBlockInsideCheck);
    states = Collections.unmodifiableList(statez);
  }

  private static final IndexBasedMovementConfiguration[] UNIVERSE = new IndexBasedMovementConfiguration[
    1 << (states.stream().mapToInt(State::bitLength).reduce(1, Integer::sum) + 1)
  ];

  static {
    Arrays.setAll(UNIVERSE, IndexBasedMovementConfiguration::new);
  }

  public static StreamCodec<ByteBuf, ByteBuf, IndexBasedMovementConfiguration> STREAM_CODEC = ByteBufStreamCodecs.INTEGER.beforeAndAfter(
	  IndexBasedMovementConfiguration::fromIndex, IndexBasedMovementConfiguration::index
  );

  private final int index;

  private IndexBasedMovementConfiguration(int index) {
    this.index = index;
  }

  public static MovementConfiguration select(
    int forward, int strafe, int reduceTicks, boolean sprint, boolean jumped, boolean handActive, boolean reduceBefore
  ) {
    IndexBasedMovementConfiguration configuration = blank();
    configuration = configuration.withForward(forward);
    configuration = configuration.withStrafe(strafe);
    configuration = configuration.withReduceTicks(reduceTicks);
    configuration = configuration.withSprintingSetTo(sprint);
    configuration = configuration.withJumped(jumped);
    configuration = configuration.withHandActive(handActive);
    configuration = configuration.withReduceBefore(reduceBefore);
    return configuration;
  }

  public static IndexBasedMovementConfiguration blank() {
    return UNIVERSE[0];
  }

  @Override
  public int forward() {
    int forwardRepresentation = forward.get(index);
    switch (forwardRepresentation) {
      case 0:
        return 0;
      case 1:
        return 1;
      case 2:
        return -1;
      default:
        throw new IllegalStateException("Unexpected value: " + forwardRepresentation);
    }
  }

  @Override
  public int strafe() {
    // can only be 0, 1, 2
    int strafeRepresentation = strafe.get(index);
    switch (strafeRepresentation) {
      case 0:
        return 0;
      case 1:
        return 1;
      case 2:
        return -1;
      default:
        throw new IllegalStateException("Unexpected value: " + strafeRepresentation);
    }
  }

  @Override
  public IndexBasedMovementConfiguration withForward(int forward) {
    if (forward < -1 || forward > 1) {
      throw new IllegalArgumentException("forward can only be -1, 0, 1");
    }
    switch (forward) {
      case -1:
        return UNIVERSE[IndexBasedMovementConfiguration.forward.set(index, 2)];
      case 0:
        return UNIVERSE[IndexBasedMovementConfiguration.forward.set(index, 0)];
      case 1:
        return UNIVERSE[IndexBasedMovementConfiguration.forward.set(index, 1)];
      default:
        throw new IllegalStateException("Unexpected value: " + forward);
    }
  }

  @Override
  public MovementConfiguration pressingW() {
    return withForward(1);
  }

  @Override
  public MovementConfiguration pressingS() {
    return withForward(-1);
  }

  @Override
  public IndexBasedMovementConfiguration withStrafe(int strafe) {
    if (strafe < -1 || strafe > 1) {
      throw new IllegalArgumentException("strafe can only be -1, 0, 1");
    }
    switch (strafe) {
      case -1:
        return UNIVERSE[IndexBasedMovementConfiguration.strafe.set(index, 2)];
      case 0:
        return UNIVERSE[IndexBasedMovementConfiguration.strafe.set(index, 0)];
      case 1:
        return UNIVERSE[IndexBasedMovementConfiguration.strafe.set(index, 1)];
      default:
        throw new IllegalStateException("Unexpected value: " + strafe);
    }
  }

  @Override
  public MovementConfiguration pressingA() {
    return withStrafe(-1);
  }

  @Override
  public MovementConfiguration pressingD() {
    return withStrafe(1);
  }

  @Override
  public MovementConfiguration withoutKeypress() {
    return withForward(0).withStrafe(0);
  }

  @Override
  public IndexBasedMovementConfiguration withKeypress(int forward, int strafe) {
    if (Math.abs(forward) > 1 || Math.abs(strafe) > 1) {
      throw new IllegalArgumentException("forward and strafe can only be -1, 0, 1");
    }
    return withForward(forward).withStrafe(strafe);
  }

  public static MovementConfiguration[] values() {
    return UNIVERSE;
  }

  @Override
  public boolean isReducing() {
    return attackReduceTicks.get(index) > 0;
  }

  @Override
  public int reduceTicks() {
    return attackReduceTicks.get(index);
  }

  @Override
  public IndexBasedMovementConfiguration withReduceTicks(int ticks) {
    return UNIVERSE[attackReduceTicks.set(index, minmax(ticks, 0, 3))];
  }

  @Override
  public MovementConfiguration withoutReducing() {
    return UNIVERSE[attackReduceTicks.set(index, 0)];
  }

  @Override
  public boolean isSprinting() {
    return sprintingState.get(index);
  }

  @Override
  public MovementConfiguration withSprinting() {
    return UNIVERSE[sprintingState.set(index, true)];
  }

  @Override
  public MovementConfiguration withoutSprinting() {
    return UNIVERSE[sprintingState.set(index, false)];
  }

  @Override
  public MovementConfiguration allowOverrideToActualMotion() {
    return UNIVERSE[noHorizontalMotionReset.set(index, false)];
  }

  @Override
  public MovementConfiguration denyOverrideToActualMotion() {
    return UNIVERSE[noHorizontalMotionReset.set(index, true)];
  }

  @Override
  public boolean overrideEndMotionToActualMotion() {
    return !noHorizontalMotionReset.get(index);
  }

  @Override
  public boolean usesAlternateBlockInsideCheck() {
    return alternativeBlockInsideCheck.get(index);
  }

  @Override
  public MovementConfiguration withAlternativeBlockInsideCheck() {
    return UNIVERSE[alternativeBlockInsideCheck.set(index, true)];
  }

  @Override
  public MovementConfiguration withoutAlternativeBlockInsideCheck() {
    return UNIVERSE[alternativeBlockInsideCheck.set(index, false)];
  }

  @Override
  public IndexBasedMovementConfiguration withSprintingSetTo(boolean sprinting) {
    return UNIVERSE[sprintingState.set(index, sprinting)];
  }

  @Override
  public boolean isJumping() {
    return jumped.get(index);
  }

  @Override
  public IndexBasedMovementConfiguration withJumped(boolean hasJumped) {
    return UNIVERSE[jumped.set(index, hasJumped)];
  }

  @Override
  public boolean isHandActive() {
    return handActive.get(index);
  }

  @Override
  public MovementConfiguration withActiveHand() {
    return UNIVERSE[handActive.set(index, true)];
  }

  @Override
  public MovementConfiguration withoutActiveHand() {
    return UNIVERSE[handActive.set(index, false)];
  }

  @Override
  public IndexBasedMovementConfiguration withHandActive(boolean hasHandActive) {
    return UNIVERSE[handActive.set(index, hasHandActive)];
  }

  @Override
  public boolean reduceBefore() {
    return reduceBefore.get(index);
  }

  public String bitString() {
    return String.format("%32s", Integer.toBinaryString(index)).replace(' ', '0');
  }

  @Override
  public IndexBasedMovementConfiguration withReduceBefore(boolean hasReduceBefore) {
    return UNIVERSE[reduceBefore.set(index, hasReduceBefore)];
  }

  private static int minmax(int val, int min, int max) {
    return Math.max(min, Math.min(max, val));
  }

  private static int starterBit;

  @Override
  public IndexBasedMovementConfiguration withJump() {
    return withJumped(true);
  }

  @Override
  public MovementConfiguration withoutJump() {
    return withJumped(false);
  }

  @Override
  public TraceImmutableMovementConfiguration withRecording() {
    return new TraceImmutableMovementConfiguration(this);
  }

  private int index() {
    return index;
  }

  private static IndexBasedMovementConfiguration fromIndex(int index) {
    if (index < 0 || index >= UNIVERSE.length) {
      throw new IllegalArgumentException("Invalid movement configuration index: " + index);
    }
    return UNIVERSE[index];
  }

  private static class BiState extends State {
    private final int slot = starterBit++;

    public int set(int before, boolean val) {
      return val ? before | (1 << slot) : before & ~(1 << slot);
    }

    public int set(int before, int val) {
      return val == 1 ? before | (1 << slot) : before & ~(1 << slot);
    }

    public boolean get(int before) {
      return ((before >> slot) & 1) == 1;
    }

    @Override
    int bitLength() {
      return 1;
    }

    @Override
    int bitMask() {
      return 1 << slot;
    }
  }

  private static class QuadState extends State {
    private final int slot;

    private QuadState() {
      this.slot = starterBit;
      starterBit += 2;
    }

    public int set(int before, int val) {
      return (before & ~(0b11 << slot)) | val << slot;
    }

    public int get(int before) {
      return (before >> slot) & 0b11;
    }

    @Override
    int bitLength() {
      return 2;
    }

    @Override
    int bitMask() {
      return 0b11 << slot;
    }
  }

  private abstract static class State {
    abstract int bitLength();

    abstract int bitMask();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    IndexBasedMovementConfiguration that = (IndexBasedMovementConfiguration) obj;
    return index == that.index;
  }

  @Override
  public int hashCode() {
    return index;
  }

  @Override
  public String toString() {
    return ("(" + keysToString() + ") " +
      (isReducing() ? "_RED" + reduceTicks() : "") +
      (isSprinting() ? "_SPR" : "") +
      (isJumping() ? "_JMP" : "") +
      (isHandActive() ? "_HA" : "")
    ).trim();
  }
}

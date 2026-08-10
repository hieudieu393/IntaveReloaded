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

package de.jpx3.intave.block.physics;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.check.movement.physics.environment.MockSimulationEnvironment;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_21;
import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_26_1_1;
import static org.junit.jupiter.api.Assertions.*;

final class HoneyPhysicsTest {
  private static final HoneyPhysics HONEY_PHYSICS = new HoneyPhysics();
  private static final BlockPosition HONEY_POSITION = new BlockPosition(0, 84, -11);
  private static final Position FROM = new Position(1.282445, 84.288475, -9.7625);

  @BeforeEach
  void setUp() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER1_21_4);
  }

  @Test
  void modernClientTestsThePreGravityVerticalMotion() {
    User user = userWithProtocol(VER_26_1_1);
    MockSimulationEnvironment environment = sideContactEnvironment();
    environment.addFallDistance(4.0D);
    double motionYBeforeGravity = -0.08D;

    Motion result = collide(
      user, environment,
      new Motion(0.000165D, postGravity(motionYBeforeGravity), 0.0D)
    );

    assertNull(result);
    assertEquals(4.0D, environment.fallDistance(), 0.0D);
  }

  @Test
  void modernClientStoresThePostGravitySlideMotion() {
    User user = userWithProtocol(VER_26_1_1);
    MockSimulationEnvironment environment = sideContactEnvironment();
    environment.addFallDistance(4.0D);
    double motionX = 0.000165D;
    double motionZ = -0.025D;

    Motion result = collide(
      user, environment,
      new Motion(motionX, postGravity(-0.13D), motionZ)
    );

    assertNotNull(result);
    assertEquals(motionX, result.motionX(), 0.0D);
    assertEquals(postGravity(-0.05D), result.motionY(), 0.0D);
    assertEquals(motionZ, result.motionZ(), 0.0D);
    assertEquals(0.0D, environment.fallDistance(), 0.0D);
  }

  @Test
  void modernClientScalesHorizontalMotionUsingThePreGravityVerticalMotion() {
    User user = userWithProtocol(VER_26_1_1);
    MockSimulationEnvironment environment = sideContactEnvironment();
    double motionYBeforeGravity = -0.2D;
    double horizontalReductionFactor = -0.05D / motionYBeforeGravity;

    Motion result = collide(
      user, environment,
      new Motion(0.3D, postGravity(motionYBeforeGravity), -0.4D)
    );

    assertNotNull(result);
    assertEquals(0.3D * horizontalReductionFactor, result.motionX(), 0.0D);
    assertEquals(postGravity(-0.05D), result.motionY(), 0.0D);
    assertEquals(-0.4D * horizontalReductionFactor, result.motionZ(), 0.0D);
  }

  @Test
  void legacyClientKeepsTheDirectSlideMotion() {
    User user = userWithProtocol(VER_1_21);
    MockSimulationEnvironment environment = sideContactEnvironment();

    Motion result = collide(user, environment, new Motion(0.3D, -0.1D, -0.4D));

    assertNotNull(result);
    assertEquals(0.3D, result.motionX(), 0.0D);
    assertEquals(-0.05D, result.motionY(), 0.0D);
    assertEquals(-0.4D, result.motionZ(), 0.0D);
  }

  private static Motion collide(
    User user,
    MockSimulationEnvironment environment,
    Motion motion
  ) {
    return HONEY_PHYSICS.entityInside(
      user, environment, HONEY_POSITION, FROM, motion, true
    );
  }

  private static MockSimulationEnvironment sideContactEnvironment() {
    MockSimulationEnvironment environment = new MockSimulationEnvironment();
    environment.setPosition(FROM.getX(), FROM.getY(), FROM.getZ());
    environment.setOnGround(false);
    return environment;
  }

  private static User userWithProtocol(int protocolVersion) {
    User user = UserFactory.createFallback();
    user.meta().protocol().setProtocolVersion(protocolVersion);
    return user;
  }

  private static double postGravity(double deltaY) {
    return (deltaY - 0.08D) * 0.98F;
  }
}

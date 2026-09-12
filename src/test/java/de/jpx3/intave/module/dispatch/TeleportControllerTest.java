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

package de.jpx3.intave.module.dispatch;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.block.cache.MockFullBlockStaticPlane;
import de.jpx3.intave.block.physics.BlockPhysics;
import de.jpx3.intave.check.movement.physics.branch.MovementSearchBranch;
import de.jpx3.intave.check.movement.physics.branch.MovementSearchInput;
import de.jpx3.intave.check.movement.physics.branch.PreviousPostTickBrancher;
import de.jpx3.intave.check.movement.physics.environment.PostTickSimulation;
import de.jpx3.intave.check.movement.physics.config.MovementConfiguration;
import de.jpx3.intave.check.movement.physics.simulator.Simulation;
import de.jpx3.intave.check.movement.physics.simulator.Simulators;
import de.jpx3.intave.packet.Relative;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.Rotation;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.test.FakeWorldFactory;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TeleportControllerTest {
  @BeforeEach
  void setServerVersion() {
    MinecraftVersion.setCurrent(new MinecraftVersion("1.8.9"));
  }

  @Test
  void repeatedAbsoluteTeleportsCannotReuseMotionFromBeforeConfirmation() {
    MovementMetadata movement = new MovementMetadata(null, null);
    // 1.8.9 NetHandlerPlayClient.handlePlayerPosLook resets all three motion
    // components before acknowledging an S08 with no relative position flags.
    for (int teleport = 0; teleport < 3; teleport++) {
      movement.setBaseMotion(0.2, -0.3, 0.4);
      movement.setPostTickMotionCandidates(Arrays.asList(
        new PostTickSimulation(new Motion(0.2, -0.3, 0.4), false),
        new PostTickSimulation(new Motion(0.3, -0.2, 0.5), true)
      ));

      TeleportController.applyTeleportMotion(movement);

      assertTrue(movement.mutableBaseMotionCopy().isZero());
      assertNextSearchUsesConfirmedMotion(movement);
    }
  }

  @Test
  void relativeRotationStillResetsAbsolutePositionMotion() {
    MovementMetadata movement = new MovementMetadata(null, null);
    movement.setBaseMotion(0.2, -0.3, 0.4);
    movement.teleportRelatives = EnumSet.of(Relative.X_ROT, Relative.Y_ROT);
    movement.setPostTickMotionCandidates(Arrays.asList(
      new PostTickSimulation(new Motion(0.2, -0.3, 0.4), false)
    ));

    TeleportController.applyTeleportMotion(movement);

    assertTrue(movement.mutableBaseMotionCopy().isZero());
    assertNextSearchUsesConfirmedMotion(movement);
  }

  @Test
  void relativeMotionKeepsItsExistingTransformAndDiscardsOldCandidates() {
    MinecraftVersion.setCurrent(new MinecraftVersion("1.21.4"));
    MovementMetadata movement = new MovementMetadata(null, null);
    movement.setBaseMotion(0.2, -0.3, 0.4);
    movement.teleportRelatives = EnumSet.of(Relative.DELTA_X, Relative.DELTA_Z);
    movement.teleportMotion.setTo(1.0, 2.0, 3.0);
    movement.setPostTickMotionCandidates(Arrays.asList(
      new PostTickSimulation(new Motion(0.2, -0.3, 0.4), false)
    ));

    TeleportController.applyTeleportMotion(movement);

    assertEquals(new Motion(1.2, 2.0, 3.4), movement.mutableBaseMotionCopy());
    assertNextSearchUsesConfirmedMotion(movement);
    assertTrue(movement.teleportMotion.isZero());
    assertTrue(movement.teleportRelatives.isEmpty());
  }

  @Test
  void groundedLegacyTeleportRetainsFirstTickSprintAcceleration() {
    User user = simulationUser(47);
    MovementMetadata movement = user.meta().movement();
    movement.onGround = movement.lastOnGround = true;

    // A second confirmation can arrive before the next ordinary movement tick.
    confirmTeleport(user, 4.0, 64.0, 2.0);
    confirmTeleport(user, 8.0, 64.0, 2.0);
    Simulation simulation = firstMovement(user, MovementConfiguration.blank().pressingW().withSprinting());

    assertEquals(legacyGroundSprintAcceleration(), simulation.offsetMotion().motionZ(), 1.0E-12);
    assertEquals(0.0, simulation.offsetMotion().motionY(), 1.0E-12);
  }

  @Test
  void groundedLegacyTeleportStillAllowsImmediateSprintJump() {
    User user = simulationUser(47);
    MovementMetadata movement = user.meta().movement();
    movement.onGround = movement.lastOnGround = true;

    confirmTeleport(user, 4.0, 64.0, 2.0);
    Simulation simulation = firstMovement(user, MovementConfiguration.blank().pressingW().withSprinting().withJump());

    assertEquals((double) 0.42F, simulation.offsetMotion().motionY(), 1.0E-12);
    assertEquals((double) 0.2F + legacyGroundSprintAcceleration(), simulation.offsetMotion().motionZ(), 1.0E-12);
  }

  @Test
  void airborneLegacyTeleportDoesNotGrantGroundAccelerationOrJump() {
    User user = simulationUser(47);
    MovementMetadata movement = user.meta().movement();
    movement.onGround = movement.lastOnGround = false;

    // Even a destination at floor height cannot change the pre-move ground state.
    confirmTeleport(user, 4.0, 64.0, 2.0);
    Simulation simulation = firstMovement(user, MovementConfiguration.blank().pressingW().withSprinting().withJump());

    assertEquals(0.0, simulation.offsetMotion().motionY(), 1.0E-12);
    float airAcceleration = (float) ((double) 0.02F + (double) 0.02F * 0.3D);
    assertEquals((double) (0.98F * airAcceleration), simulation.offsetMotion().motionZ(), 1.0E-12);
  }

  @Test
  void newerTeleportHandlersAlsoPreserveGroundState() {
    // 1.9.4 handlePlayerPosLook and 1.21.4 handleMovePlayer both send a false
    // ground bit in the acknowledgement without clearing the entity's onGround.
    for (int protocol : new int[]{110, 769}) {
      MinecraftVersion.setCurrent(new MinecraftVersion(protocol == 110 ? "1.9.4" : "1.21.4"));
      User user = simulationUser(protocol);
      MovementMetadata movement = user.meta().movement();
      movement.onGround = movement.lastOnGround = true;

      confirmTeleport(user, 4.0, 64.0, 2.0);

      assertTrue(movement.lastOnGround());
      assertTrue(movement.onGround());
    }
  }

  private static double legacyGroundSprintAcceleration() {
    // EntityLivingBase.moveEntityWithHeading: preserve the client's float order.
    float friction = 0.6F * 0.91F;
    return (double) (0.98F * ((0.1F * 1.3F) * (0.16277136F / (friction * friction * friction))));
  }

  private static void confirmTeleport(User user, double x, double y, double z) {
    user.meta().movement().teleportLocation = new Location(user.player().getWorld(), x, y, z);
    TeleportController.applyTeleportState(user, x, y, z);
  }

  private static Simulation firstMovement(User user, MovementConfiguration configuration) {
    MovementMetadata movement = user.meta().movement();
    movement.updateMovement(movement.position(), Rotation.zero());
    var environment = movement.mutableView();
    Motion motion = Simulators.PLAYER.simulatePreTick(user, environment.mutableBaseMotionCopy(), environment);
    return Simulators.PLAYER.simulateTick(user, motion, environment, configuration);
  }

  private static User simulationUser(int protocol) {
    BlockPhysics.setup(MinecraftVersion.current());
    var world = FakeWorldFactory.createWorld((method, args) -> switch (method) {
      case "isChunkLoaded", "isChunkInUse" -> true;
      case "isThundering", "hasStorm" -> false;
      default -> null;
    });
    var location = new Location(world, 0.0, 64.0, 0.0);
    UUID id = UUID.randomUUID();
    var player = FakePlayerFactory.createPlayer((method, args) -> switch (method) {
      case "getWorld" -> world;
      case "getLocation" -> location;
      case "getUniqueId" -> id;
      default -> null;
    });
    var plane = MockFullBlockStaticPlane.createWithHorizontalPlaneAt(63);
    User user = UserFactory.createTestUserFor(player, (ignored, key) -> switch (key) {
      case "blockCache" -> plane;
      case "protocolVersion" -> protocol;
      default -> null;
    });
    UserRepository.manuallyRegisterUser(player, user);
    MovementMetadata movement = user.meta().movement();
    movement.setSimulator(Simulators.PLAYER);
    movement.sprinting = movement.lastSprinting = true;
    movement.updateMovement(Position.of(0.0, 64.0, 0.0), Rotation.zero());
    movement.setVerifiedLastPosition(movement.position(), "teleport test seed");
    movement.setBaseMotion(0.2, -0.0784000015258789, 0.3);
    return user;
  }

  private static void assertNextSearchUsesConfirmedMotion(MovementMetadata movement) {
    MovementSearchInput input = MovementSearchInput.forTick(null, null, movement, false);
    MovementSearchBranch confirmed = MovementSearchBranch.blank(input);
    List<MovementSearchBranch> branches = new ArrayList<>();
    new PreviousPostTickBrancher().branch(input, confirmed, branches);

    assertEquals(1, branches.size(), "A teleport must discard the previous tick's motion alternatives");
    assertSame(confirmed, branches.get(0), "The next search must keep the confirmed teleport motion");
    assertTrue(movement.postTickMotionCandidates().isEmpty());
  }
}

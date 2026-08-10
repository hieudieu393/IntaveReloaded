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

package de.jpx3.intave.check.movement.physics.misc;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.block.cache.MockFullBlockStaticPlane;
import de.jpx3.intave.check.movement.physics.config.MovementConfiguration;
import de.jpx3.intave.check.movement.physics.environment.Pose;
import de.jpx3.intave.check.movement.physics.simulator.Simulation;
import de.jpx3.intave.check.movement.physics.simulator.Simulators;
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
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_26_1_1;
import static org.junit.jupiter.api.Assertions.*;

final class BaseSimulatorSwimmingStateTest {
	private static final double EPSILON = 1.0E-12;
	private static final Position POSITION = Position.of(
		-31.540329138839745,
		83.84266627212745,
		7.086249519113167
	);
	private static final Rotation ROTATION = Rotation.of(
		-10.541455268859863F,
		60.358905792236328F
	);
	private static final Motion BASE_MOTION = new Motion(
		0.044581407146740304,
		-0.2741051533143235,
		-0.061315526771318574
	);
	private static final MovementConfiguration FORWARD_SPRINT =
		MovementConfiguration.blank().pressingW().withSprinting();

	@BeforeEach
	void setUp() {
		MinecraftVersion.setCurrent(new MinecraftVersion("26.1.2"));
	}

	@Test
	void physicalSwimmingPoseWithoutLogicalSwimmingDoesNotApplyPitchAdjustment() {
		MovementMetadata environment = environment(Pose.SWIMMING, false);

		Simulation simulation = Simulators.PLAYER.simulateTick(
			environment.user(), BASE_MOTION.copy(), environment, FORWARD_SPRINT
		);

		assertEquals(Pose.SWIMMING, environment.pose());
		assertFalse(environment.isSwimming());
		assertEquals(BASE_MOTION.motionY(), simulation.offsetMotion().motionY(), EPSILON);
	}

	@Test
	void logicalSwimmingAppliesPitchAdjustmentBeforePhysicalPoseCatchesUp() {
		MovementMetadata environment = environment(Pose.FALL_FLYING, true);
		double lookY = environment.lookVector().getY();
		double expectedY = BASE_MOTION.motionY()
			+ (lookY - BASE_MOTION.motionY()) * 0.085D;

		Simulation simulation = Simulators.PLAYER.simulateTick(
			environment.user(), BASE_MOTION.copy(), environment, FORWARD_SPRINT
		);

		assertEquals(Pose.FALL_FLYING, environment.pose());
		assertTrue(environment.isSwimming());
		assertEquals(expectedY, simulation.offsetMotion().motionY(), EPSILON);
	}

	private static MovementMetadata environment(Pose pose, boolean swimming) {
		World world = FakeWorldFactory.createWorld(
			(methodName, _) -> switch (methodName) {
				case "isChunkLoaded", "isChunkInUse" -> true;
				case "isThundering", "hasStorm" -> false;
				default -> null;
			}
		);
		Location location = POSITION.toLocation(world);
		UUID playerId = new UUID(pose.ordinal(), swimming ? 1L : 0L);
		Player player = FakePlayerFactory.createPlayer(
			(methodName, _) -> switch (methodName) {
				case "getWorld" -> world;
				case "getLocation" -> location;
				case "getUniqueId" -> playerId;
				default -> null;
			}
		);
		User user = UserFactory.createTestUserFor(player, (usr, key) -> switch (key) {
			case "blockCache" -> new MockFullBlockStaticPlane();
			case "protocolVersion" -> VER_26_1_1;
			default -> null;
		});
		UserRepository.manuallyRegisterUser(player, user);

		MovementMetadata environment = user.meta().movement();
		environment.updateMovement(POSITION, ROTATION);
		environment.setVerifiedLastPosition(POSITION, "swimming state test seed");
		environment.setLastPosition(POSITION);
		environment.setInWater(true);
		environment.setPose(pose);
		environment.setSwimming(swimming);
		environment.gliding = true;
		environment.setLastOnGround(false);
		return environment;
	}
}

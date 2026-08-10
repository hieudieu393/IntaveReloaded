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
import de.jpx3.intave.check.movement.physics.environment.MockSimulationEnvironment;
import de.jpx3.intave.check.movement.physics.simulator.Simulation;
import de.jpx3.intave.check.movement.physics.simulator.Simulators;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.test.FakeWorldFactory;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_15;
import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_16;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class BaseSimulatorLavaJumpTest {
	private static final double EPSILON = 1.0E-12;
	private static final double BASE_MOTION_Y = -0.02D;
	private static final double GROUND_JUMP_Y = 0.42D;
	private static final double LIQUID_JUMP_Y = BASE_MOTION_Y + (double) 0.04F;
	private static final double SPRINT_JUMP_IMPULSE = (double) 0.2F;
	private static final MovementConfiguration SPRINT_JUMP =
		MovementConfiguration.blank().withJump().withSprinting();

	@BeforeEach
	void setUp() {
		MinecraftVersion.setCurrent(new MinecraftVersion("26.1.2"));
	}

	@Test
	void modernGroundedLavaAtExactThresholdUsesGroundJump() {
		Simulation simulation = simulateLavaJump(VER_1_16, true, 0.4D);

		assertJumpMotion(simulation, GROUND_JUMP_Y, SPRINT_JUMP_IMPULSE);
	}

	@Test
	void modernGroundedLavaAboveThresholdUsesLiquidJump() {
		Simulation simulation = simulateLavaJump(VER_1_16, true, Math.nextUp(0.4D));

		assertJumpMotion(simulation, LIQUID_JUMP_Y, 0.0D);
	}

	@Test
	void modernAirborneLavaAtShallowDepthUsesLiquidJump() {
		Simulation simulation = simulateLavaJump(VER_1_16, false, 0.4D);

		assertJumpMotion(simulation, LIQUID_JUMP_Y, 0.0D);
	}

	@Test
	void legacyGroundedLavaAtShallowDepthUsesLiquidJump() {
		Simulation simulation = simulateLavaJump(VER_1_15, true, 0.4D);

		assertJumpMotion(simulation, LIQUID_JUMP_Y, 0.0D);
	}

	private static Simulation simulateLavaJump(
		int protocolVersion,
		boolean onGround,
		double lavaDepth
	) {
		Position position = Position.of(0.5D, 50.0D, 0.5D);
		World world = FakeWorldFactory.createWorld(
			(methodName, _) -> switch (methodName) {
				case "isChunkLoaded", "isChunkInUse" -> true;
				case "isThundering", "hasStorm" -> false;
				default -> null;
			}
		);
		Location location = position.toLocation(world);
		UUID playerId = UUID.randomUUID();
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
			case "protocolVersion" -> protocolVersion;
			default -> null;
		});
		UserRepository.manuallyRegisterUser(player, user);

		MockSimulationEnvironment environment = new MockSimulationEnvironment(user);
		environment.setLastPosition(position);
		environment.setVerifiedLastPosition(position, "lava jump test seed");
		environment.setBoundingBox(
			BoundingBox.fromBounds(0.2D, 50.0D, 0.2D, 0.8D, 51.8D, 0.8D)
		);
		environment.setBaseMotion(0.0D, BASE_MOTION_Y, 0.0D);
		environment.setJumpMotion(GROUND_JUMP_Y);
		environment.setOnGround(onGround);
		environment.setLastOnGround(onGround);
		environment.setInWater(false);
		environment.setInLava(true);
		environment.setLavaDepth(lavaDepth);
		environment.setYaw(0.0F);

		return Simulators.PLAYER.simulateTick(
			user,
			new Motion(0.0D, BASE_MOTION_Y, 0.0D),
			environment,
			SPRINT_JUMP
		);
	}

	private static void assertJumpMotion(
		Simulation simulation,
		double expectedY,
		double expectedZ
	) {
		Motion offsetMotion = simulation.offsetMotion();
		Motion actualMotion = simulation.actualMotion();
		assertEquals(0.0D, offsetMotion.motionX(), EPSILON);
		assertEquals(expectedY, offsetMotion.motionY(), EPSILON);
		assertEquals(expectedZ, offsetMotion.motionZ(), EPSILON);
		assertEquals(offsetMotion.motionX(), actualMotion.motionX(), EPSILON);
		assertEquals(offsetMotion.motionY(), actualMotion.motionY(), EPSILON);
		assertEquals(offsetMotion.motionZ(), actualMotion.motionZ(), EPSILON);
	}
}

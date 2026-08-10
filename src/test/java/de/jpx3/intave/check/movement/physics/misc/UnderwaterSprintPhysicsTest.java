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
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.cache.MockFullBlockStaticPlane;
import de.jpx3.intave.check.movement.physics.config.MovementConfiguration;
import de.jpx3.intave.check.movement.physics.environment.Pose;
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

import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_21_5;
import static org.junit.jupiter.api.Assertions.*;

final class UnderwaterSprintPhysicsTest {
	private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
	private static final Position POSITION = Position.of(0, 50, 0);
	private static final Rotation ROTATION = Rotation.zero();

	private User user;

	@BeforeEach
	void setUp() {
		MinecraftVersion.setCurrent(MinecraftVersions.VER1_21_4);
		World world = FakeWorldFactory.createWorld(
			(methodName, _) -> switch (methodName) {
				case "isChunkLoaded", "isChunkInUse" -> true;
				case "isThundering", "hasStorm" -> false;
				default -> null;
			}
		);
		Location location = POSITION.toLocation(world);
		Player player = FakePlayerFactory.createPlayer(
			(methodName, _) -> switch (methodName) {
				case "getWorld" -> world;
				case "getLocation" -> location;
				case "getUniqueId" -> PLAYER_ID;
				default -> null;
			}
		);
		MockFullBlockStaticPlane blockCache = new MockFullBlockStaticPlane();
		user = UserFactory.createTestUserFor(player, (usr, key) -> switch (key) {
			case "blockCache" -> blockCache;
			case "protocolVersion" -> VER_1_21_5;
			default -> null;
		});
		UserRepository.manuallyRegisterUser(player, user);
	}

	@Test
	void underwaterSprintStartRemainsSearchableBeforeSwimmingPoseUpdates() {
		MovementMetadata metadata = user.meta().movement();
		metadata.updateMovement(POSITION, ROTATION);
		metadata.setVerifiedLastPosition(POSITION, "test seed");
		metadata.setLastPosition(POSITION);

		metadata.setPose(Pose.STANDING);
		metadata.setInWater(true);
		metadata.setEyesInWater(true);
		metadata.setLastSprinting(false);
		metadata.setSprinting(true);
		metadata.hasSprintSpeed = false;

		assertFalse(metadata.shouldHaveSwimmingPose());

		metadata.updateMovement(POSITION, ROTATION);

		assertTrue(metadata.sprintingAllowed());

		metadata.setEyesInWater(false);
		metadata.updateMovement(POSITION, ROTATION);

		metadata.setPose(Pose.SWIMMING);
		metadata.setSwimming(true);
		metadata.setLastSprinting(true);
		metadata.updateMovement(POSITION, ROTATION);

		assertTrue(metadata.sprintingAllowed());
	}

	@Test
	void physicalSwimmingPoseDoesNotStartLogicalSwimmingInShallowWater() {
		MovementMetadata metadata = user.meta().movement();
		metadata.setPose(Pose.SWIMMING);
		metadata.setSwimming(false);
		metadata.setInWater(true);
		metadata.setEyesInWater(false);
		metadata.setLastMovementConfiguration(
			MovementConfiguration.blank().withSprinting()
		);

		metadata.updateSwimming();

		assertFalse(metadata.isSwimming());
		assertFalse(metadata.shouldHaveSwimmingPose());
		assertEquals(Pose.SWIMMING, metadata.pose());
	}

	@Test
	void logicalSwimmingContinuesInShallowWaterUntilSprintingStops() {
		MovementMetadata metadata = user.meta().movement();
		metadata.setPose(Pose.FALL_FLYING);
		metadata.setSwimming(true);
		metadata.setInWater(true);
		metadata.setEyesInWater(false);
		metadata.setLastMovementConfiguration(
			MovementConfiguration.blank().withSprinting()
		);

		metadata.updateSwimming();

		assertTrue(metadata.isSwimming());
		assertTrue(metadata.shouldHaveSwimmingPose());
		assertEquals(Pose.FALL_FLYING, metadata.pose());

		metadata.setLastMovementConfiguration(MovementConfiguration.blank());
		metadata.updateSwimming();

		assertFalse(metadata.isSwimming());
	}
}

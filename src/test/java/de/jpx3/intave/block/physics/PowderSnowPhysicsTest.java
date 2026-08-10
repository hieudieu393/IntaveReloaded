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
import de.jpx3.intave.block.cache.PlaybackBlockCacheView;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.check.movement.physics.environment.MockSimulationEnvironment;
import de.jpx3.intave.module.test.record.MaterialVariantStore;
import de.jpx3.intave.module.test.record.MovementRecording;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.test.FakeWorldFactory;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_21_4;
import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_21_5;
import static org.junit.jupiter.api.Assertions.*;

final class PowderSnowPhysicsTest {
  private static final BlockPosition POWDER_POSITION = BlockPosition.of(10, 20, 30);
  private final PowderSnowPhysics physics = new PowderSnowPhysics();

  @BeforeEach
  void setUp() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER1_21_4);
    physics.setupFor(MinecraftVersion.current());
  }

  @Test
  void entityInsideReadsTheCandidateEnvironmentPosition() {
    User user = userWithPowderSnow(VER_1_21_4);
    user.meta().movement().positionX = 0.5D;
    user.meta().movement().positionY = 0.0D;
    user.meta().movement().positionZ = 0.5D;
    MockSimulationEnvironment candidate = new MockSimulationEnvironment(user);
    candidate.setPosition(10.5D, 20.0D, 30.5D);

    physics.entityInside(
      user, candidate, POWDER_POSITION,
      Position.of(10.5D, 20.0D, 30.5D), Motion.newEmpty(), true
    );

    assertEquals(new Vector(0.9F, 1.5F, 0.9F), candidate.motionMultiplier());
  }

  @Test
  void entityInsideShapeBecomesContextualFrom1215() {
    User user = userWithPowderSnow(VER_1_21_5);
    MockSimulationEnvironment candidate = new MockSimulationEnvironment(user);
    candidate.addFallDistance(3.0D);

    BlockShape shape = physics.entityInsideCollisionShape(user, candidate, POWDER_POSITION);

    assertFalse(shape.isCubic());
    assertEquals(20.9D, shape.max(de.jpx3.intave.share.Direction.Axis.Y_AXIS), 0.000001D);
  }

  @Test
  void legacyEntityInsideShapeRemainsAFullCube() {
    User user = userWithPowderSnow(VER_1_21_4);
    MockSimulationEnvironment candidate = new MockSimulationEnvironment(user);
    candidate.addFallDistance(3.0D);

    assertTrue(physics.entityInsideCollisionShape(user, candidate, POWDER_POSITION).isCubic());
  }

  private static User userWithPowderSnow(int protocolVersion) {
    MovementRecording recording = MovementRecording.create();
    PlaybackBlockCacheView blockCache = new PlaybackBlockCacheView(recording);
    blockCache.updateBlocks(Map.of(
      POWDER_POSITION, MaterialVariantStore.of(Material.POWDER_SNOW, 0)
    ));
    World world = FakeWorldFactory.createWorld(
      (methodName, arguments) -> "isChunkLoaded".equals(methodName) ? true : null
    );
    UUID playerId = UUID.randomUUID();
    Player player = FakePlayerFactory.createPlayer(
      (methodName, arguments) -> {
        switch (methodName) {
          case "getWorld":
            return world;
          case "getLocation":
            return new Location(world, 0.5D, 0.0D, 0.5D);
          case "getUniqueId":
            return playerId;
          default:
            return null;
        }
      }
    );
    return UserFactory.createTestUserFor(player, (user, key) -> {
      switch (key) {
        case "blockCache":
          return blockCache;
        case "protocolVersion":
          return protocolVersion;
        default:
          return null;
      }
    });
  }
}

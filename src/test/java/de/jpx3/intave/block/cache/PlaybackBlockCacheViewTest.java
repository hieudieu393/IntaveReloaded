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

package de.jpx3.intave.block.cache;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.variant.BlockVariant;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.module.test.record.MaterialVariantStore;
import de.jpx3.intave.module.test.record.MovementRecording;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.test.FakeWorldFactory;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class PlaybackBlockCacheViewTest {
	@Test
	void volatileAccessReturnsRecordedVariantWithoutNativeRegistry() {
		MinecraftVersion.setCurrent(MinecraftVersions.VER1_21_4);
		MovementRecording recording = MovementRecording.create();
		recording.recordBlockVariant(Material.STONE, 7, variantWithDrag());
		BlockVariantRegister.overrideVariants(recording.blockVariants());
		PlaybackBlockCacheView blockCache = new PlaybackBlockCacheView(recording);
		BlockPosition position = BlockPosition.of(12, 34, 56);
		blockCache.updateBlocks(Map.of(position, MaterialVariantStore.of(Material.STONE, 7)));

		World world = FakeWorldFactory.createWorld(
			(methodName, _) -> "isChunkLoaded".equals(methodName) ? true : null
		);
		UUID playerId = UUID.randomUUID();
		Player player = FakePlayerFactory.createPlayer(
			(methodName, _) -> switch (methodName) {
				case "getWorld" -> world;
				case "getLocation" -> new Location(world, 0, 0, 0);
				case "getUniqueId" -> playerId;
				default -> null;
			}
		);
		User user = UserFactory.createTestUserFor(player, (usr, key) -> switch (key) {
			case "blockCache" -> blockCache;
			case "protocolVersion" -> 47;
			default -> null;
		});

		BlockVariant variant = VolatileBlockAccess.variantAccess(
			user, world, position.getX(), position.getY(), position.getZ()
		);

		assertTrue(variant.<Boolean>propertyOf("drag"));
	}

	private static BlockVariant variantWithDrag() {
		return new BlockVariant() {
			@Override
			public Set<String> propertyNames() {
				return Set.of("drag");
			}

			@Override
			@SuppressWarnings("unchecked")
			public <T> T propertyOf(String name) {
				return (T) Boolean.TRUE;
			}

			@Override
			public <T extends Enum<T>> T enumProperty(Class<T> klass, String name) {
				return null;
			}

			@Override
			public int index() {
				return 7;
			}

			@Override
			public void dumpStates() {
			}
		};
	}
}

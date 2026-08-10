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

import de.jpx3.intave.player.Enchantments;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ServerlessBukkitRegistryTest {
	@Test
	void enchantmentsDoNotInitializeBukkitRegistryWithoutServer() {
		assertNull(Bukkit.getServer());
		assertNull(Enchantments.ENCHANTMENT_RIPTIDE);
		assertEquals(0.0F, Enchantments.resolveDepthStriderModifier(null));
	}

	@Test
	void materialSolidityDoesNotRequireBukkitRegistryWithoutServer() {
		assertNull(Bukkit.getServer());
		assertTrue(MaterialMagic.blockSolid(Material.STONE));
		assertFalse(MaterialMagic.blockSolid(Material.AIR));
		assertFalse(MaterialMagic.blockSolid(Material.FIRE));
		assertFalse(MaterialMagic.blockSolid(Material.WATER));
		assertFalse(MaterialMagic.blockSolid(Material.LAVA));
		assertFalse(MaterialMagic.blockSolid(Material.LADDER));
	}
}

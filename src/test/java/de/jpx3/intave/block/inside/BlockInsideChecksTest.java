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

package de.jpx3.intave.block.inside;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.check.movement.physics.config.MovementConfiguration;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.MutableBlockPosition;
import de.jpx3.intave.share.Position;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class BlockInsideChecksTest {
	@BeforeAll
	static void setupVersion() {
		MinecraftVersion.setCurrent(MinecraftVersions.VER26_1_1);
	}

	@Test
	void selectsEveryDistinct21xInsideCheck() {
		assertInstanceOf(v21BlockInsideCheck.class, BlockInsideChecks.suitableForProtocol(767).getFirst());
		assertInstanceOf(v212BlockInsideCheck.class, BlockInsideChecks.suitableForProtocol(768).getFirst());
		assertEquals(v214BlockInsideCheck.class, BlockInsideChecks.suitableForProtocol(769).getFirst().getClass());
		assertEquals(v215BlockInsideCheck.class, BlockInsideChecks.suitableForProtocol(770).getFirst().getClass());
		assertEquals(v216BlockInsideCheck.class, BlockInsideChecks.suitableForProtocol(771).getFirst().getClass());
		assertEquals(v216BlockInsideCheck.class, BlockInsideChecks.suitableForProtocol(772).getFirst().getClass());
		assertEquals(v2110BlockInsideCheck.class, BlockInsideChecks.suitableForProtocol(774).getFirst().getClass());
		assertEquals(v2110BlockInsideCheck.class, BlockInsideChecks.suitableForProtocol(775).getFirst().getClass());
	}

	@Test
	void exposesTheAmbiguous21_9CheckForExplicitSelection() {
		assertInstanceOf(v219BlockInsideCheck.class, BlockInsideChecks.v21_9());
		assertEquals(v219BlockInsideCheck.class, BlockInsideChecks.v21_9().getClass());
	}

	@Test
	void selectsBothChecksForSharedProtocol773ConfigurationBranches() {
		MovementConfiguration v21_10 = MovementConfiguration.blank().withoutAlternativeBlockInsideCheck();
		MovementConfiguration v21_9 = MovementConfiguration.blank().withAlternativeBlockInsideCheck();
		var sharedProtocolChecks = BlockInsideChecks.suitableForProtocol(773);

		assertEquals(2, sharedProtocolChecks.size());
		assertEquals(v2110BlockInsideCheck.class, sharedProtocolChecks.get(1).getClass());
		assertEquals(v219BlockInsideCheck.class, sharedProtocolChecks.get(0).getClass());
		assertEquals(v2110BlockInsideCheck.class, BlockInsideChecks.suitableForProtocol(773, v21_10).getClass());
		assertEquals(v219BlockInsideCheck.class, BlockInsideChecks.suitableForProtocol(773, v21_9).getClass());
		assertEquals(v216BlockInsideCheck.class, BlockInsideChecks.suitableForProtocol(772, v21_9).getClass());
		assertEquals(1, BlockInsideChecks.suitableForProtocol(774).size());
		assertEquals(1, BlockInsideChecks.suitableForProtocol(775).size());
	}

	@Test
	void appliesTheInsideOrFastFlagOnlyFrom21_10() {
		v219BlockInsideCheck v21_9 = new v219BlockInsideCheck();
		v2110BlockInsideCheck v21_10 = new v2110BlockInsideCheck();
		Position origin = Position.of(0.0, 0.0, 0.0);
		BoundingBox finalBox = new BoundingBox(0.0, 0.0, 0.0, 0.6, 1.8, 0.6);
		MutableBlockPosition outside = new MutableBlockPosition(10, 10, 10);

		assertTrue(v21_9.insideBlockOrTooFast(origin, origin, finalBox, outside));
		assertFalse(v21_10.insideBlockOrTooFast(origin, origin, finalBox, outside));
		assertTrue(v21_10.insideBlockOrTooFast(origin, origin, finalBox, new MutableBlockPosition(0, 0, 0)));
		assertTrue(v21_10.insideBlockOrTooFast(origin, Position.of(1.0, 0.0, 0.0), finalBox, outside));
	}
}

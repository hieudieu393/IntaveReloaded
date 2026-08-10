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

package de.jpx3.intave.block.fluid;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class v13FluidflowTest {
	@Test
	void modernFluidSurfacePreservesDoubleCoordinatePrecision() {
		float fluidHeight = 8.0F / 9.0F;
		double legacySurface = v13Fluidflow.fluidSurfaceY(83, fluidHeight, false);
		double modernSurface = v13Fluidflow.fluidSurfaceY(83, fluidHeight, true);

		assertEquals((double) ((float) 83 + fluidHeight), legacySurface);
		assertEquals(83.0D + (double) fluidHeight, modernSurface);
		assertTrue(modernSurface > legacySurface);
	}
}

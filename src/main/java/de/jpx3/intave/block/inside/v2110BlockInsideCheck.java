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

import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.MutableBlockPosition;
import de.jpx3.intave.share.Position;

public final class v2110BlockInsideCheck extends v219BlockInsideCheck {
	@Override
	protected boolean insideBlockOrTooFast(
		Position from, Position to,
		BoundingBox finalBox, MutableBlockPosition blockPosition
	) {
		return from.distanceSquared(to) > MathHelper.square(0.9999900000002526)
			|| finalBox.intersects(blockPosition);
	}
}

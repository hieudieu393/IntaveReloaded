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

import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.share.*;
import de.jpx3.intave.user.User;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.bukkit.Material;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class v219BlockInsideCheck extends BlockInsideCheck {
	@Override
	public void checkInsideBlocks(
		User user, SimulationEnvironment environment,
		Motion motion, List<EntityMovement> movements
	) {
		LongSet visitedBlocks = new LongOpenHashSet();
		for (EntityMovement movement : movements) {
			Position from = movement.from();
			Position to = movement.to();
			Motion actualMovement = to.subtractToMotion(from);
			int remainingSteps = 16;

			if (movement.axisDependentOriginalMovement().isPresent() && actualMovement.lengthSquared() > 0.0) {
				Motion originalMovement = movement.axisDependentOriginalMovement().get();
				for (Direction.Axis axis : Direction.axisStepOrder(originalMovement)) {
					double partial = actualMovement.partialMotionIn(axis);
					if (partial != 0.0) {
						Position partialPosition = from.relative(axis.positive(), partial);
						remainingSteps -= checkSegment(
							user, environment, motion, from, partialPosition,
							visitedBlocks, remainingSteps
						);
						from = partialPosition;
					}
				}
			} else {
				remainingSteps -= checkSegment(
					user, environment, motion, movement.from(), to,
					visitedBlocks, 16
				);
			}

			if (remainingSteps <= 0) {
				checkSegment(user, environment, motion, to, to, visitedBlocks, 1);
			}
		}
	}

	private int checkSegment(
		User user, SimulationEnvironment environment,
		Motion motion, Position from, Position to,
		LongSet visitedBlocks, int limit
	) {
		BoundingBox box = BoundingBox.fromPosition(user, environment, to).shrink(1.0E-5F);
		AtomicInteger scanned = new AtomicInteger();
		BlockGetter.forEachBlockIntersectedBetween(from, to, box, (position, scannedBlocks) -> {
			if (scannedBlocks >= limit) {
				return false;
			}
			scanned.set(scannedBlocks);
			BlockPosition blockPosition = position.toBlockPosition();
			Material material = materialAt(user, blockPosition);
			if (!isAir(material)
				&& intersectsEntityInsideShape(user, environment, material, blockPosition, from, to)
				&& visitedBlocks.add(position.asLong())) {
				applyBlockEffect(
					user, environment, motion, blockPosition, from,
					insideBlockOrTooFast(from, to, box, position), material
				);
			}
			return true;
		});
		return scanned.get() + 1;
	}

	protected boolean insideBlockOrTooFast(
		Position from,
		Position to,
		BoundingBox finalBox,
		MutableBlockPosition blockPosition
	) {
		// The flag did not exist until 1.21.10, so pre-1.21.10 blocks always apply.
		return true;
	}
}

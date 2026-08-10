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
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.share.*;
import de.jpx3.intave.user.User;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.bukkit.Material;

import java.util.List;
import java.util.Optional;

import static de.jpx3.intave.share.ClientMath.floor;

public class v212BlockInsideCheck extends BlockInsideCheck {
	@Override
	public void checkInsideBlocks(
		User user,
		SimulationEnvironment environment,
		Motion motion,
		List<EntityMovement> movements
	) {
		LongSet visitedBlocks = new LongOpenHashSet();
		BoundingBox finalBox = BoundingBox.fromPosition(user, environment, environment.position()).shrink(1.0E-5F);
		for (EntityMovement movement : movements) {
			checkMovement(user, environment, motion, movement, visitedBlocks, finalBox);
		}
	}

	protected void checkMovement(
		User user,
		SimulationEnvironment environment,
		Motion motion,
		EntityMovement movement,
		LongSet visitedBlocks,
		BoundingBox finalBox
	) {
		checkSegment(
			user, environment, motion,
			movement.from(), movement.to(), visitedBlocks,
			boxForMovement(user, environment, movement.to(), finalBox)
		);
	}

	protected void checkSegment(
		User user,
		SimulationEnvironment environment,
		Motion motion,
		Position from,
		Position to,
		LongSet visitedBlocks,
		BoundingBox box
	) {
		forEachBlockIntersectedBetween(from, to, box, usesTravelEpsilon(), (position, scannedBlocks) -> {
			BlockPosition blockPosition = position.toBlockPosition();
			Material material = materialAt(user, blockPosition);
			if (!isAir(material)
				&& visitedBlocks.add(position.asLong())
				&& intersectsEntityInsideShape(user, environment, material, blockPosition, from, to)) {
				applyBlockEffect(user, environment, motion, blockPosition, from, true, material);
			}
			return true;
		});
	}

	protected BoundingBox boxForMovement(
		User user,
		SimulationEnvironment environment,
		Position to,
		BoundingBox finalBox
	) {
		return finalBox;
	}

	protected boolean usesTravelEpsilon() {
		return true;
	}

	protected final boolean forEachBlockIntersectedBetween(
		Position from,
		Position to,
		BoundingBox box,
		boolean travelEpsilon,
		BlockStepVisitor visitor
	) {
		Motion move = to.subtractToMotion(from);
		if (move.lengthSquared() < ClientMath.square(0.99999F)) {
			return visitFinalBox(box, 0, null, visitor);
		}

		LongSet visited = new LongOpenHashSet();
		Position minCorner = Position.of(box.minX, box.minY, box.minZ);
		Position toCorner = minCorner;
		Position fromCorner;
		if (travelEpsilon) {
			Motion epsilon = move.copy().normalize().multiply(1.0E-7D);
			toCorner = minCorner.add(epsilon);
			fromCorner = minCorner.add(move.reversed()).add(epsilon.reversed());
		} else {
			fromCorner = minCorner.add(move.reversed());
		}

		int steps = addCollisionsAlongTravel(visited, fromCorner, toCorner, box, visitor);
		if (steps < 0) {
			return false;
		}
		return visitFinalBox(box, steps + 1, visited, visitor);
	}

	private boolean visitFinalBox(
		BoundingBox box,
		int step,
		LongSet visited,
		BlockStepVisitor visitor
	) {
		int minX = floor(box.minX);
		int minY = floor(box.minY);
		int minZ = floor(box.minZ);
		int maxX = floor(box.maxX);
		int maxY = floor(box.maxY);
		int maxZ = floor(box.maxZ);
		MutableBlockPosition cursor = new MutableBlockPosition();

		// BlockPos.betweenClosed has X as its fastest-changing coordinate.
		for (int z = minZ; z <= maxZ; z++) {
			for (int y = minY; y <= maxY; y++) {
				for (int x = minX; x <= maxX; x++) {
					cursor.set(x, y, z);
					if ((visited == null || !visited.contains(cursor.asLong())) && !visitor.visit(cursor, step)) {
						return false;
					}
				}
			}
		}
		return true;
	}

	private int addCollisionsAlongTravel(
		LongSet visited,
		Position from,
		Position to,
		BoundingBox box,
		BlockStepVisitor visitor
	) {
		Motion move = to.subtractToMotion(from);
		int x = floor(from.getX());
		int y = floor(from.getY());
		int z = floor(from.getZ());
		int signX = ClientMath.sign(move.motionX());
		int signY = ClientMath.sign(move.motionY());
		int signZ = ClientMath.sign(move.motionZ());
		double deltaX = signX == 0 ? Double.MAX_VALUE : signX / move.motionX();
		double deltaY = signY == 0 ? Double.MAX_VALUE : signY / move.motionY();
		double deltaZ = signZ == 0 ? Double.MAX_VALUE : signZ / move.motionZ();
		double nextX = deltaX * (signX > 0 ? 1.0 - ClientMath.fraction(from.getX()) : ClientMath.fraction(from.getX()));
		double nextY = deltaY * (signY > 0 ? 1.0 - ClientMath.fraction(from.getY()) : ClientMath.fraction(from.getY()));
		double nextZ = deltaZ * (signZ > 0 ? 1.0 - ClientMath.fraction(from.getZ()) : ClientMath.fraction(from.getZ()));
		int steps = 0;
		MutableBlockPosition cursor = new MutableBlockPosition();

		while (nextX <= 1.0 || nextY <= 1.0 || nextZ <= 1.0) {
			if (nextX < nextY) {
				if (nextX < nextZ) {
					x += signX;
					nextX += deltaX;
				} else {
					z += signZ;
					nextZ += deltaZ;
				}
			} else if (nextY < nextZ) {
				y += signY;
				nextY += deltaY;
			} else {
				z += signZ;
				nextZ += deltaZ;
			}

			if (steps++ > 16) {
				break;
			}

			Optional<Position> clip = BoundingBox.clip(x, y, z, x + 1, y + 1, z + 1, from, to);
			if (clip.isPresent()) {
				Position clipPosition = clip.get();
				double clipX = MathHelper.minmax(x + 1.0E-5F, clipPosition.getX(), x + 1.0 - 1.0E-5F);
				double clipY = MathHelper.minmax(y + 1.0E-5F, clipPosition.getY(), y + 1.0 - 1.0E-5F);
				double clipZ = MathHelper.minmax(z + 1.0E-5F, clipPosition.getZ(), z + 1.0 - 1.0E-5F);
				int maxX = floor(clipX + box.sizeX());
				int maxY = floor(clipY + box.sizeY());
				int maxZ = floor(clipZ + box.sizeZ());

				// This loop order is explicit in BlockGetter.addCollisionsAlongTravel.
				for (int collisionX = x; collisionX <= maxX; collisionX++) {
					for (int collisionY = y; collisionY <= maxY; collisionY++) {
						for (int collisionZ = z; collisionZ <= maxZ; collisionZ++) {
							cursor.set(collisionX, collisionY, collisionZ);
							if (visited.add(cursor.asLong()) && !visitor.visit(cursor, steps)) {
								return -1;
							}
						}
					}
				}
			}
		}
		return steps;
	}
}

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
import de.jpx3.intave.share.*;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.Optional;

public class BlockGetter {
	// BlockGetter.forEachBlockIntersectedBetween
	public static boolean forEachBlockIntersectedBetween(
		Position from, Position to,
		BoundingBox boundingBox,
		BlockStepVisitor visitor
	) {
		Motion motion = to.subtractToMotion(from);
		if (motion.lengthSquared() < ClientMath.square(0.00001f)) {
			for (MutableBlockPosition position : boundingBox.blockPositionsBetween()) {
				if (!visitor.visit(position, 0)) {
					return false;
				}
			}
			return true;
		} else {
			LongSet longSet = new LongOpenHashSet();
			BoundingBox fromBox = boundingBox.move(motion.reversed());
			for (MutableBlockPosition position : fromBox.blockPositionsBetweenDirectional(motion)) {
				if (!visitor.visit(position, 0)) {
					return false;
				}
				longSet.add(position.asLong());
			}
			int i = addCollisionsAlongTravel(longSet, motion, boundingBox, visitor);
			if (i < 0) {
				return false;
			} else {
				for (MutableBlockPosition position : boundingBox.blockPositionsBetweenDirectional(motion)) {
					if (longSet.add(position.asLong()) && !visitor.visit(position, i + 1)) {
						return false;
					}
				}
				return true;
			}
		}
	}


	// BlockGetter.addCollisionsAlongTravel
	protected static int addCollisionsAlongTravel(
		LongSet longSet,
		Motion move,
		BoundingBox box,
		BlockStepVisitor visitor
	) {
		double boxXSize = box.sizeX();
		double boxYSize = box.sizeY();
		double boxZSize = box.sizeZ();
		RawVector3d vec3i = move.furthestCorner();
		Position boxCenter = box.center();

		Position positionTo = Position.of(
			boxCenter.getX() + boxXSize * 0.5 * vec3i.x(),
			boxCenter.getY() + boxYSize * 0.5 * vec3i.y(),
			boxCenter.getZ() + boxZSize * 0.5 * vec3i.z()
		);
		Position positionFrom = positionTo.add(move.reversed());
		int fromX = ClientMath.floor(positionFrom.getX());
		int fromY = ClientMath.floor(positionFrom.getY());
		int fromZ = ClientMath.floor(positionFrom.getZ());
		int moveXSgn = ClientMath.sign(move.motionX());
		int moveYSgn = ClientMath.sign(move.motionY());
		int moveZSgn = ClientMath.sign(move.motionZ());
		double d3 = moveXSgn == 0 ? Double.MAX_VALUE : moveXSgn / move.motionX;
		double d4 = moveYSgn == 0 ? Double.MAX_VALUE : moveYSgn / move.motionY;
		double d5 = moveZSgn == 0 ? Double.MAX_VALUE : moveZSgn / move.motionZ;
		double d6 = d3 * (moveXSgn > 0 ? 1.0 - ClientMath.fraction(positionFrom.getX()) : ClientMath.fraction(positionFrom.getX()));
		double d7 = d4 * (moveYSgn > 0 ? 1.0 - ClientMath.fraction(positionFrom.getY()) : ClientMath.fraction(positionFrom.getY()));
		double d8 = d5 * (moveZSgn > 0 ? 1.0 - ClientMath.fraction(positionFrom.getZ()) : ClientMath.fraction(positionFrom.getZ()));

		int k1 = 0;

		while (d6 <= 1.0 || d7 <= 1.0 || d8 <= 1.0) {
			if (d6 < d7) {
				if (d6 < d8) {
					fromX += moveXSgn;
					d6 += d3;
				} else {
					fromZ += moveZSgn;
					d8 += d5;
				}
			} else if (d7 < d8) {
				fromY += moveYSgn;
				d7 += d4;
			} else {
				fromZ += moveZSgn;
				d8 += d5;
			}

			Optional<Position> clip = BoundingBox.clip(fromX, fromY, fromZ, fromX + 1, fromY + 1, fromZ + 1, positionFrom, positionTo);
			if (clip.isPresent()) {
				k1++;
				Position clipPos = clip.get();
				double clipPosX = MathHelper.minmax(fromX + 1.0e-5f, clipPos.getX(), fromX + 1.0f - 1.0e-5f);
				double clipPosY = MathHelper.minmax(fromY + 1.0e-5f, clipPos.getY(), fromY + 1.0f - 1.0e-5f);
				double clipPosZ = MathHelper.minmax(fromZ + 1.0e-5f, clipPos.getZ(), fromZ + 1.0f - 1.0e-5f);
				int toX = ClientMath.floor(clipPosX - boxXSize * vec3i.x());
				int toY = ClientMath.floor(clipPosY - boxYSize * vec3i.y());
				int toZ = ClientMath.floor(clipPosZ - boxZSize * vec3i.z());
				int k2 = k1;
				BlockPositions positions = BoundingBox.blockPositionBetweenDirectional(
					fromX, fromY, fromZ,
					toX, toY, toZ,
					move
				);

				for (MutableBlockPosition position : positions) {
					if (longSet.add(position.asLong()) && !visitor.visit(position, k2)) {
						return -1;
					}
				}
			}
		}

		return k1;
	}
}

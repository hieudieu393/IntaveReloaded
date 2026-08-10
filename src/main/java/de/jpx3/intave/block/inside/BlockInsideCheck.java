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

import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.physics.BlockPhysics;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;
import org.bukkit.Material;

import java.util.List;

public abstract class BlockInsideCheck {
	public abstract void checkInsideBlocks(
		User user,
		SimulationEnvironment environment,
		Motion motion,
		List<EntityMovement> movements
	);

	protected final void applyBlockEffect(
		User user,
		SimulationEnvironment environment,
		Motion motion,
		BlockPosition blockPosition,
		Position movementFrom,
		boolean insideBlockOrTooFast
	) {
		Material material = VolatileBlockAccess.typeAccess(user, blockPosition);
		if (isAir(material)) {
			return;
		}
		applyBlockEffect(
			user, environment, motion, blockPosition,
			movementFrom, insideBlockOrTooFast, material
		);
	}

	protected final void applyBlockEffect(
		User user,
		SimulationEnvironment environment,
		Motion motion,
		BlockPosition blockPosition,
		Position movementFrom,
		boolean insideBlockOrTooFast,
		Material material
	) {
		Motion newMotion = BlockPhysics.entityInside(
			user, material, environment, blockPosition,
			movementFrom, motion, insideBlockOrTooFast
		);
		if (newMotion != null) {
			motion.setTo(newMotion);
		}
	}

	protected final Material materialAt(User user, BlockPosition position) {
		return VolatileBlockAccess.typeAccess(user, position);
	}

	protected final boolean intersectsEntityInsideShape(
		User user,
		SimulationEnvironment environment,
		Material material,
		BlockPosition blockPosition,
		Position from,
		Position to
	) {
		BlockShape shape = BlockPhysics.entityInsideCollisionShape(
			user, material, environment, blockPosition
		);
		return intersectsEntityInsideShape(user, environment, from, to, shape);
	}

	protected final boolean intersectsEntityInsideShape(
		User user,
		SimulationEnvironment environment,
		Position from,
		Position to,
		BlockShape shape
	) {
		if (shape.isCubic()) {
			return true;
		}
		BoundingBox fromBox = BoundingBox.fromPosition(user, environment, from);
		return fromBox.collidedAlongVector(to.subtractToMotion(from), shape);
	}

	protected static boolean isAir(Material material) {
		String materialName = material.name();
		return material == Material.AIR
			|| materialName.equals("CAVE_AIR")
			|| materialName.equals("VOID_AIR");
	}
}

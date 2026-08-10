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

import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;

import java.util.Optional;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public final class EntityMovement {
	private final Position from;
	private final Position to;
	private final Optional<Motion> axisDependentOriginalMovement;

	public EntityMovement(
		Position from, Position to,
		Optional<Motion> axisDependentOriginalMovement
	) {
		this.from = from;
		this.to = to;
		this.axisDependentOriginalMovement = axisDependentOriginalMovement;
	}

	public Position from() {
		return from;
	}

	public Position to() {
		return to;
	}

	public Optional<Motion> axisDependentOriginalMovement() {
		return axisDependentOriginalMovement;
	}

	public static EntityMovement of(Position from, Position to) {
		return new EntityMovement(from, to, Optional.empty());
	}

	public static EntityMovement of(Position from, Position to, Motion axisDependentOriginalMovement) {
		return new EntityMovement(from, to, Optional.ofNullable(axisDependentOriginalMovement));
	}
}
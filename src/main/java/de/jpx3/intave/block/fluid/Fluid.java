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

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

public interface Fluid {
	StreamCodec<ByteBuf, ByteBuf, Fluid> STREAM_CODEC = StreamCodec.dispatchBuilder(Fluid.class, ByteBufStreamCodecs.UNSIGNED_BYTE)
		.subtype(1, Dry.class, () -> Dry.STREAM_CODEC)
		.subtype(2, Water.class, () -> Water.STREAM_CODEC)
		.subtype(3, Lava.class, () -> Lava.STREAM_CODEC)
		.build();

	boolean isDry();

	boolean isOfWater();

	boolean isOfLava();

	float height();

	int level();

	boolean falling();

	boolean isSource();

	default boolean affectsFlow(Fluid other) {
		return other.isDry() || other.similarTo(this);
	}

	default boolean similarTo(Fluid other) {
		return isOfWater() == other.isOfWater() && isOfLava() == other.isOfLava();
	}
}

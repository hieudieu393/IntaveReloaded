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

package de.jpx3.intave.check.movement.physics.recording.action;

import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.module.test.record.TickRange;
import de.jpx3.intave.module.test.record.action.Action;
import de.jpx3.intave.module.test.record.action.ReceiveVelocity;
import de.jpx3.intave.share.Motion;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ActionCodecTest {

	@Test
	public void testReceiveVelocity() {
		ReceiveVelocity receiveVelocity = new ReceiveVelocity(Motion.random(), TickRange.random());

		ByteBuf buf = Unpooled.buffer();
		StreamCodec<ByteBuf, ByteBuf, Action> actionCodec = Action.STREAM_CODEC;

		actionCodec.encode(buf, receiveVelocity);
		Action reconstructed = actionCodec.decode(buf);

		assertInstanceOf(ReceiveVelocity.class, reconstructed);
		assertEquals(receiveVelocity, reconstructed);
	}
}
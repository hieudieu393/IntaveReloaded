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

package de.jpx3.intave.packet.reader;

import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAnimation;

public final class AnimationReader extends EntityReader {
	private Animation animation;

	@Override
	protected void read() {
		WrapperPlayClientAnimation wrapper = new WrapperPlayClientAnimation(receiveEvent());
		animation = wrapper.getHand() == InteractionHand.OFF_HAND ? Animation.SWING_OFFHAND : Animation.SWING;
	}

	public Animation animation() {
		return animation;
	}

	@Override
	public void release() {
		animation = null;
		super.release();
	}

	public enum Animation {
		SWING,
		HURT,
		WAKEUP,
		SWING_OFFHAND,
		CRIT,
		CRIT_MAGIC
	}
}

/* Copyright 2026 Intave */
package de.jpx3.intave.packet.reader;

import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import de.jpx3.intave.annotate.Nullable;

public final class BlockDigReader extends BlockPositionReader {
  private WrapperPlayClientPlayerDigging wrapper;

  @Override
  protected void read() {
    wrapper = new WrapperPlayClientPlayerDigging(receiveEvent());
    blockPosition(wrapper.getBlockPosition());
  }

  public @Nullable DiggingAction action() {
    return wrapper == null ? null : wrapper.getAction();
  }

  public int faceId() {
    return wrapper == null ? 255 : wrapper.getBlockFaceId();
  }

  public int sequenceNumber() {
    return wrapper == null ? 0 : wrapper.getSequence();
  }

  @Override
  public void release() {
    wrapper = null;
    super.release();
  }
}

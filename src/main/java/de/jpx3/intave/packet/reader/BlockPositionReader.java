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

import com.github.retrooper.packetevents.util.Vector3i;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.share.BlockPosition;

/**
 * Base reader for packets whose first payload field is a Minecraft block position.
 * Packet-specific readers whose layout differs (for example placement packets) override
 * {@link #read()} and {@link #blockPosition()}.
 */
public class BlockPositionReader extends AbstractPacketReader {
  private BlockPosition blockPosition;

  @Override
  protected void read() {
    Vector3i position = rawPacket().readBlockPosition();
    blockPosition = position == null ? null : BlockPosition.fromPacketEvents(position);
  }

  public @Nullable BlockPosition nativeBlockPosition() {
    return blockPosition;
  }

  public @Nullable BlockPosition blockPosition() {
    return blockPosition;
  }

  protected final void blockPosition(Vector3i position) {
    blockPosition = position == null ? null : BlockPosition.fromPacketEvents(position);
  }

  @Override
  public void release() {
    blockPosition = null;
    super.release();
  }
}

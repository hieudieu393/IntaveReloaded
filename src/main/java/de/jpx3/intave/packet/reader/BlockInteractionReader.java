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

import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientUseItem;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.user.User;
import org.bukkit.util.Vector;

/** Native PacketEvents reader for use-item / use-item-on interactions. */
public final class BlockInteractionReader extends BlockPositionReader {
  private WrapperPlayClientPlayerBlockPlacement placement;
  private WrapperPlayClientUseItem useItem;
  private int sequenceNumber;
  private boolean hasProtocolSequenceNumber;
  private boolean hasArtificialSequenceNumber;

  @Override
  protected void read() {
    placement = null;
    useItem = null;
    sequenceNumber = 0;
    hasProtocolSequenceNumber = false;
    hasArtificialSequenceNumber = false;

    if (packetType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
      placement = new WrapperPlayClientPlayerBlockPlacement(receiveEvent());
      blockPosition(placement.getBlockPosition());
      sequenceNumber = placement.getSequence();
      hasProtocolSequenceNumber = receiveEvent().getUser().getClientVersion().isNewerThanOrEquals(
        com.github.retrooper.packetevents.protocol.player.ClientVersion.V_1_19
      );
      return;
    }

    if (packetType() == PacketType.Play.Client.USE_ITEM) {
      useItem = new WrapperPlayClientUseItem(receiveEvent());
      blockPosition(null);
      sequenceNumber = useItem.getSequence();
      hasProtocolSequenceNumber = receiveEvent().getUser().getClientVersion().isNewerThanOrEquals(
        com.github.retrooper.packetevents.protocol.player.ClientVersion.V_1_19
      );
      return;
    }

    // No supported block interaction wrapper for this type. Keep the reader empty rather than
    // guessing field offsets from a generic packet buffer.
    blockPosition(null);
  }

  @Nullable
  public Direction direction() {
    BlockFace face = placement == null ? null : placement.getFace();
    if (face == null || face == BlockFace.OTHER) {
      return null;
    }
    return Direction.valueOf(face.name());
  }

  @Nullable
  public Vector facingVector() {
    if (placement == null) {
      return null;
    }
    Vector3f cursor = placement.getCursorPosition();
    return cursor == null ? null : new Vector(cursor.getX(), cursor.getY(), cursor.getZ());
  }

  public int enumDirection() {
    BlockFace face = placement == null ? null : placement.getFace();
    return face == null ? 255 : face.getFaceValue();
  }

  public boolean insideBlock() {
    return placement != null && placement.getInsideBlock().orElse(false);
  }

  public int sequenceNumber(User user) {
    if (hasProtocolSequenceNumber) {
      return sequenceNumber;
    }
    if (!hasArtificialSequenceNumber) {
      hasArtificialSequenceNumber = true;
      sequenceNumber = user.meta().connection().simulatedBlockAckNum++;
    }
    return sequenceNumber;
  }

  @Override
  public void release() {
    placement = null;
    useItem = null;
    sequenceNumber = 0;
    hasProtocolSequenceNumber = false;
    hasArtificialSequenceNumber = false;
    super.release();
  }
}

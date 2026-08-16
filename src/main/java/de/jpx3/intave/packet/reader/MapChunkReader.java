package de.jpx3.intave.packet.reader;

import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;

public final class MapChunkReader extends AbstractPacketReader implements ChunkCoordinateReader {
  private Column column;

  @Override
  protected void read() {
    column = new WrapperPlayServerChunkData(sendEvent()).getColumn();
  }

  @Override
  public int[] xCoordinates() {
    return new int[]{column.getX()};
  }

  @Override
  public int[] zCoordinates() {
    return new int[]{column.getZ()};
  }

  @Override
  public void release() {
    column = null;
    super.release();
  }
}

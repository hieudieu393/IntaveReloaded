package de.jpx3.intave.packet.reader;

import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkDataBulk;

public final class MapChunkBulkReader extends AbstractPacketReader implements ChunkCoordinateReader {
  private WrapperPlayServerChunkDataBulk wrapper;

  @Override
  protected void read() {
    wrapper = new WrapperPlayServerChunkDataBulk(sendEvent());
  }

  @Override
  public int[] xCoordinates() {
    return wrapper.getX().clone();
  }

  @Override
  public int[] zCoordinates() {
    return wrapper.getZ().clone();
  }

  @Override
  public void release() {
    wrapper = null;
    super.release();
  }
}

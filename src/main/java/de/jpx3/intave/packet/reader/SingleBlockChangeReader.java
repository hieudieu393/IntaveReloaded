package de.jpx3.intave.packet.reader;

import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import de.jpx3.intave.share.BlockPosition;

import java.util.Collections;
import java.util.List;

public final class SingleBlockChangeReader extends AbstractPacketReader implements BlockChanges {
  private BlockPosition blockPosition;
  private WrappedBlockState blockState;

  @Override
  protected void read() {
    WrapperPlayServerBlockChange wrapper = new WrapperPlayServerBlockChange(sendEvent());
    blockPosition = BlockPosition.fromPacketEvents(wrapper.getBlockPosition());
    blockState = wrapper.getBlockState();
  }

  @Override
  public List<BlockPosition> blockPositions() {
    return Collections.singletonList(blockPosition);
  }

  @Override
  public List<WrappedBlockState> blockDataList() {
    return Collections.singletonList(blockState);
  }

  @Override
  public void release() {
    blockPosition = null;
    blockState = null;
    super.release();
  }
}

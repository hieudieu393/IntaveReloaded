package de.jpx3.intave.packet.reader;

import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import de.jpx3.intave.share.BlockPosition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MultiBlockChangeReader extends AbstractPacketReader implements BlockChanges {
  private List<BlockPosition> blockPositions = Collections.emptyList();
  private List<WrappedBlockState> blockDataList = Collections.emptyList();

  @Override
  protected void read() {
    WrapperPlayServerMultiBlockChange wrapper = new WrapperPlayServerMultiBlockChange(sendEvent());
    WrapperPlayServerMultiBlockChange.EncodedBlock[] blocks = wrapper.getBlocks();
    ClientVersion clientVersion = sendEvent().getUser().getClientVersion();
    List<BlockPosition> positions = new ArrayList<>(blocks.length);
    List<WrappedBlockState> states = new ArrayList<>(blocks.length);
    for (WrapperPlayServerMultiBlockChange.EncodedBlock block : blocks) {
      positions.add(new BlockPosition(block.getX(), block.getY(), block.getZ()));
      states.add(block.getBlockState(clientVersion));
    }
    blockPositions = positions;
    blockDataList = states;
  }

  @Override
  public List<BlockPosition> blockPositions() {
    return blockPositions;
  }

  @Override
  public List<WrappedBlockState> blockDataList() {
    return blockDataList;
  }

  @Override
  public void release() {
    blockPositions = Collections.emptyList();
    blockDataList = Collections.emptyList();
    super.release();
  }
}

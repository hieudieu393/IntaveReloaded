package de.jpx3.intave.packet.reader;

import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import de.jpx3.intave.share.BlockPosition;

import java.util.List;

public interface BlockChanges extends PacketReader {
  List<BlockPosition> blockPositions();
  List<WrappedBlockState> blockDataList();
}

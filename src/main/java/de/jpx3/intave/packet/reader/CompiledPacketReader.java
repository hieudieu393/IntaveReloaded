package de.jpx3.intave.packet.reader;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;

public abstract class CompiledPacketReader extends AbstractPacketReader {
  @Override
  public void enter(ProtocolPacketEvent event) {
    super.enter(event);
    compile();
  }

  public abstract void compile();
}

package de.jpx3.intave.packet.reader;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;

public interface PacketReader extends AutoCloseable {
  void enter(ProtocolPacketEvent event);
  void flush();
  void release();

  @Override
  default void close() {
    release();
  }

  void releaseSafe();
}

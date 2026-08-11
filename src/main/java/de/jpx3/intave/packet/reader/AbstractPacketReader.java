package de.jpx3.intave.packet.reader;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import de.jpx3.intave.IntaveControl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public abstract class AbstractPacketReader implements PacketReader {
  private static final Map<PacketTypeCommon, AtomicLong> MISSING_FLUSHES_BY_TYPE = new ConcurrentHashMap<>();

  private ProtocolPacketEvent event;

  @Override
  public void enter(ProtocolPacketEvent event) {
    if (this.event != null) {
      long count = MISSING_FLUSHES_BY_TYPE
        .computeIfAbsent(event.getPacketType(), ignored -> new AtomicLong())
        .incrementAndGet();
      if (count < 5 && IntaveControl.NOTIFY_MISSING_PACKET_FLUSHES) {
        System.err.println("Missing packet reader release for " + String.valueOf(event.getPacketType()) + " (" + count + ")");
      }
    }
    this.event = event;
    read();
  }

  /** Decode the current native PacketEvents packet into reader-local state. */
  protected void read() {
  }

  @Override
  public void flush() {
  }

  @Override
  public void release() {
    event = null;
  }

  @Override
  public void releaseSafe() {
    if (event != null) {
      release();
    }
  }

  protected final ProtocolPacketEvent event() {
    if (event == null) {
      throw new IllegalStateException("Packet reader is not entered");
    }
    return event;
  }

  protected final PacketReceiveEvent receiveEvent() {
    return (PacketReceiveEvent) event();
  }

  protected final PacketSendEvent sendEvent() {
    return (PacketSendEvent) event();
  }

  protected final PacketWrapper<?> rawPacket() {
    ProtocolPacketEvent current = event();
    return current instanceof PacketReceiveEvent
      ? new PacketWrapper<>((PacketReceiveEvent) current, false)
      : new PacketWrapper<>((PacketSendEvent) current, false);
  }

  public final PacketTypeCommon packetType() {
    return event().getPacketType();
  }
}

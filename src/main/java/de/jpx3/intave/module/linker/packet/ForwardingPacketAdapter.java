package de.jpx3.intave.module.linker.packet;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;

import java.util.Collection;

/** One native PacketEvents listener per packet type, forwarding to Intave's ordered subscriptions. */
final class ForwardingPacketAdapter extends PacketListenerAbstract {
  private final PacketTypeCommon packetType;
  private final Collection<FilteringPacketAdapter> targetList;

  ForwardingPacketAdapter(PacketTypeCommon packetType, Collection<FilteringPacketAdapter> targetList) {
    super(PacketListenerPriority.LOWEST);
    this.packetType = packetType;
    this.targetList = targetList;
  }

  @Override
  public void onPacketReceive(PacketReceiveEvent event) {
    if (event.getPacketType() != packetType) return;
    for (FilteringPacketAdapter target : targetList) {
      target.invoke(event);
    }
  }

  @Override
  public void onPacketSend(PacketSendEvent event) {
    if (event.getPacketType() != packetType) return;
    for (FilteringPacketAdapter target : targetList) {
      target.invoke(event);
    }
  }

  @Override
  public String toString() {
    return targetList.toString();
  }
}

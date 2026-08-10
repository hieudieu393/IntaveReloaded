package com.comphenix.protocol.events;
public enum ListenerPriority {
  LOWEST, LOW, NORMAL, HIGH, HIGHEST, MONITOR;
  public com.github.retrooper.packetevents.event.PacketListenerPriority packetEvents() {
    return com.github.retrooper.packetevents.event.PacketListenerPriority.valueOf(name());
  }
}

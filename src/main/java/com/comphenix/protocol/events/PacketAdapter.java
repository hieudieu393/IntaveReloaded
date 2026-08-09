package com.comphenix.protocol.events;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.comphenix.protocol.PacketType;
import org.bukkit.plugin.Plugin;
import java.util.*;

public abstract class PacketAdapter extends PacketListenerAbstract {
  protected Plugin plugin;
  private final Set<PacketType> types = new HashSet<>();

  public PacketAdapter(Plugin plugin, PacketType... types) { this(plugin, ListenerPriority.NORMAL, Arrays.asList(types)); }
  public PacketAdapter(Plugin plugin, ListenerPriority priority, Iterable<? extends PacketType> types) {
    super(priority.packetEvents()); this.plugin=plugin; types.forEach(this.types::add);
  }
  public PacketAdapter(Plugin plugin, ListenerPriority priority, Iterable<? extends PacketType> types, ListenerOptions... options) {
    this(plugin, priority, types);
  }
  public PacketAdapter(Plugin plugin, ListenerPriority priority, List<PacketType> types, ListenerOptions[] options) {
    this(plugin, priority, (Iterable<? extends PacketType>) types);
  }

  private boolean accepts(com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon type) {
    PacketType translated = PacketType.fromHandle(type);
    return translated != null && (types.isEmpty() || types.contains(translated));
  }
  @Override public final void onPacketReceive(PacketReceiveEvent event) {
    if (accepts(event.getPacketType())) onPacketReceiving(PacketEvent.from(event));
  }
  @Override public final void onPacketSend(PacketSendEvent event) {
    if (accepts(event.getPacketType())) onPacketSending(PacketEvent.from(event));
  }
  public void onPacketReceiving(PacketEvent event) {}
  public void onPacketSending(PacketEvent event) {}
  public Plugin getPlugin() { return plugin; }
}

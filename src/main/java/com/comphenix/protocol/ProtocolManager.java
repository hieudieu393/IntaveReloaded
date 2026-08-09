package com.comphenix.protocol;

import com.github.retrooper.packetevents.PacketEvents;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import java.util.*;

public class ProtocolManager {
  private final Set<PacketAdapter> listeners = Collections.synchronizedSet(new LinkedHashSet<>());
  public PacketContainer createPacket(PacketType type) { return new PacketContainer(type); }
  public void sendServerPacket(Player player, PacketContainer packet) { sendServerPacket(player, packet, true); }
  public void sendServerPacket(Player player, PacketContainer packet, boolean filters) {
    if (packet == null || packet.wrapper() == null) return;
    if (filters) PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet.wrapper());
    else PacketEvents.getAPI().getPlayerManager().sendPacketSilently(player, packet.wrapper());
  }
  public void receiveClientPacket(Player player, PacketContainer packet) {
    if (packet != null && packet.wrapper() != null) PacketEvents.getAPI().getPlayerManager().receivePacket(player, packet.wrapper());
  }
  public void recieveClientPacket(Player player, PacketContainer packet) { receiveClientPacket(player, packet); }
  public void addPacketListener(PacketAdapter adapter) { listeners.add(adapter); PacketEvents.getAPI().getEventManager().registerListener(adapter); }
  public void removePacketListener(PacketAdapter adapter) { listeners.remove(adapter); PacketEvents.getAPI().getEventManager().unregisterListener(adapter); }
  public void removePacketListeners(Plugin plugin) {
    for (PacketAdapter listener : new ArrayList<>(listeners)) if (listener.getPlugin() == plugin) removePacketListener(listener);
  }
  public Collection<PacketAdapter> getPacketListeners() { return Collections.unmodifiableSet(listeners); }
  public int getProtocolVersion(Player player) { return PacketEvents.getAPI().getPlayerManager().getClientVersion(player).getProtocolVersion(); }
  public Object getAsynchronousManager() { return PacketEvents.getAPI().getEventManager(); }
  public boolean isClosed() { return PacketEvents.getAPI() == null || PacketEvents.getAPI().isTerminated(); }
}

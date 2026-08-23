package de.jpx3.intave.packet.nativeapi;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import de.jpx3.intave.packet.nativeapi.events.PacketAdapter;
import de.jpx3.intave.packet.nativeapi.events.NativePacket;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import java.util.*;

public class PacketRuntimeManager {
  private final Set<PacketAdapter> listeners = Collections.synchronizedSet(new LinkedHashSet<>());
  public NativePacket createPacket(PacketType type) { return new NativePacket(type); }
  public NativePacket createPacket(PacketTypeCommon type) { return new NativePacket(type); }
  public void sendServerPacket(Player player, NativePacket packet) { sendServerPacket(player, packet, true); }
  public void sendServerPacket(Player player, NativePacket packet, boolean filters) {
    if (packet == null || packet.wrapper() == null) return;
    if (filters) PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet.wrapper());
    else PacketEvents.getAPI().getPlayerManager().sendPacketSilently(player, packet.wrapper());
  }
  public void receiveClientPacket(Player player, NativePacket packet) {
    if (packet != null && packet.wrapper() != null) PacketEvents.getAPI().getPlayerManager().receivePacket(player, packet.wrapper());
  }
  public void recieveClientPacket(Player player, NativePacket packet) { receiveClientPacket(player, packet); }
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

package de.jpx3.intave.packet.nativeapi;
import com.github.retrooper.packetevents.PacketEvents;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
public final class PacketRuntime {
  private static final PacketRuntimeManager MANAGER = new de.jpx3.intave.packet.nativeapi.injector.PacketFilterManager();
  private PacketRuntime() {}
  public static PacketRuntimeManager getPacketRuntimeManager() { return MANAGER; }
  public static Plugin getPlugin() { return Bukkit.getPluginManager().getPlugin("packetevents"); }
  public static boolean available() { return PacketEvents.getAPI() != null && PacketEvents.getAPI().isInitialized(); }
}

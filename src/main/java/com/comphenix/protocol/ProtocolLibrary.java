package com.comphenix.protocol;
import com.github.retrooper.packetevents.PacketEvents;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
public final class ProtocolLibrary {
  private static final ProtocolManager MANAGER = new com.comphenix.protocol.injector.PacketFilterManager();
  private ProtocolLibrary() {}
  public static ProtocolManager getProtocolManager() { return MANAGER; }
  public static Plugin getPlugin() { return Bukkit.getPluginManager().getPlugin("packetevents"); }
  public static boolean available() { return PacketEvents.getAPI() != null && PacketEvents.getAPI().isInitialized(); }
}

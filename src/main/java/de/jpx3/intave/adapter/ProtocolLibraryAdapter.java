package de.jpx3.intave.adapter;

import com.github.retrooper.packetevents.PacketEvents;
import de.jpx3.intave.access.InvalidDependencyException;
import org.bukkit.Bukkit;

/**
 * Legacy-named compatibility entry point retained for existing Intave call sites.
 * The packet backend is PacketEvents; ProtocolLib is no longer required or loaded.
 */
@Deprecated
public final class ProtocolLibraryAdapter {
  private ProtocolLibraryAdapter() {}

  @Deprecated
  public static MinecraftVersion serverVersion() {
    return MinecraftVersion.current();
  }

  public static boolean protocolLibAvailable() {
    return packetEventsAvailable();
  }

  public static boolean packetEventsAvailable() {
    return Bukkit.getPluginManager().getPlugin("packetevents") != null
      && PacketEvents.getAPI() != null
      && PacketEvents.getAPI().isInitialized();
  }

  public static void checkIfOutdated() {
    if (!packetEventsAvailable()) {
      throw new InvalidDependencyException("PacketEvents 2.x is required and must be initialized before IntaveReloaded");
    }
  }
}

/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 */
package de.jpx3.intave.adapter;

import com.github.retrooper.packetevents.PacketEvents;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/** Ensures the runtime required by IntaveReloaded is available. */
public final class ComponentLoader {
  private final IntavePlugin plugin;

  public ComponentLoader(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  public void prepareComponents() {
    // PacketEvents is declared as a hard plugin dependency in plugin.yml.
  }

  public void loadComponents() {
    if (!isSupportedServerVersion()) {
      throw new IntaveInternalException(
        "IntaveReloaded supports Minecraft 1.21 and newer only (server: " + Bukkit.getBukkitVersion() + ")"
      );
    }

    Plugin packetEvents = Bukkit.getPluginManager().getPlugin("packetevents");
    if (packetEvents == null) {
      throw new IntaveInternalException("PacketEvents is required to run IntaveReloaded");
    }
    if (!packetEvents.isEnabled()) {
      Bukkit.getPluginManager().enablePlugin(packetEvents);
    }
    if (PacketEvents.getAPI() == null || !PacketEvents.getAPI().isInitialized()) {
      throw new IntaveInternalException("PacketEvents must be initialized before IntaveReloaded");
    }
  }

  private static boolean isSupportedServerVersion() {
    String version = Bukkit.getBukkitVersion();
    if (version == null || version.isEmpty()) {
      return false;
    }

    String minecraftVersion = version.split("-", 2)[0];
    String[] parts = minecraftVersion.split("\\.");
    if (parts.length < 2) {
      return false;
    }

    try {
      int major = Integer.parseInt(parts[0]);
      int minor = Integer.parseInt(parts[1]);
      return major > 1 || (major == 1 && minor >= 21);
    } catch (NumberFormatException ignored) {
      return false;
    }
  }
}

package de.jpx3.intave.module;

import com.github.retrooper.packetevents.PacketEvents;
import java.util.Arrays;

final class Requirements {
  public static Requirement none() {
    return new NoRequirement();
  }

  /** Legacy method name retained so existing module declarations remain source compatible. */
  public static Requirement protocolLib() {
    return requiresPlugin("packetevents")
      .and(() -> PacketEvents.getAPI() != null && PacketEvents.getAPI().isInitialized());
  }

  /** PacketEvents has no ProtocolLib-4 split; any initialized PacketEvents backend satisfies it. */
  public static Requirement protocolLib4() {
    return protocolLib();
  }

  public static Requirement intaveEnabled() {
    return requiresPlugin("IntaveReloaded");
  }

  public static Requirement requiresPlugin(String plugin) {
    return new PluginRequirement(plugin);
  }

  public static Requirement requiresPlugins(String... plugins) {
    return new PluginRequirement(plugins);
  }

  public static Requirement mergeAnd(Requirement... requirements) {
    return () -> Arrays.stream(requirements).allMatch(Requirement::fulfilled);
  }

  public static Requirement mergeOr(Requirement... requirements) {
    return () -> Arrays.stream(requirements).anyMatch(Requirement::fulfilled);
  }
}

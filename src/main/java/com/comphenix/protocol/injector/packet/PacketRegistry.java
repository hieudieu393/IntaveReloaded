package com.comphenix.protocol.injector.packet;
import com.comphenix.protocol.PacketType;
import java.util.*;
public final class PacketRegistry {
  private PacketRegistry() {}
  public static Set<PacketType> getClientPacketTypes() { return new LinkedHashSet<>(PacketType.clientValues()); }
  public static Set<PacketType> getServerPacketTypes() { return new LinkedHashSet<>(PacketType.serverValues()); }
  public static PacketType getPacketType(Class<?> packetClass) {
    if (packetClass == null) return null;
    for (PacketType type : PacketType.values()) {
      Class<?> wrapper = type.getPacketClass();
      if (wrapper != Object.class && wrapper.isAssignableFrom(packetClass)) return type;
    }
    String simple = packetClass.getSimpleName();
    for (PacketType type : PacketType.values()) {
      if (type.name().equalsIgnoreCase(simple) || (type.handle() != null && type.handle().getName().equalsIgnoreCase(simple))) return type;
    }
    return null;
  }
}

package com.comphenix.protocol.injector.packet;
import com.comphenix.protocol.PacketType;
import java.util.*;
public final class PacketRegistry {
  private PacketRegistry() {}
  public static Set<PacketType> getClientPacketTypes() {
    Set<PacketType> s=new LinkedHashSet<>(); for(PacketType t: PacketType.values()) if(t.handle()!=null && t.handle().getSide().name().equalsIgnoreCase("CLIENT")) s.add(t); return s;
  }
  public static PacketType getPacketType(Class<?> packetClass) {
    if (packetClass == null) return null;
    String simple = packetClass.getSimpleName();
    for (PacketType type : PacketType.values()) {
      if (type.name().equalsIgnoreCase(simple)) return type;
      if (type.handle() != null && (type.handle().getName().equalsIgnoreCase(simple) || (type.handle().getWrapperClass() != null && type.handle().getWrapperClass().isAssignableFrom(packetClass)))) return type;
    }
    return null;
  }
  public static Set<PacketType> getServerPacketTypes() {
    Set<PacketType> s=new LinkedHashSet<>(); for(PacketType t: PacketType.values()) if(t.handle()!=null && t.handle().getSide().name().equalsIgnoreCase("SERVER")) s.add(t); return s;
  }
}

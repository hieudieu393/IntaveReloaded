package com.comphenix.protocol.injector.packet;
import com.comphenix.protocol.PacketType;
import java.util.*;
public final class PacketRegistry {
  private PacketRegistry() {}
  public static Set<PacketType> getClientPacketTypes() {
    Set<PacketType> s=new LinkedHashSet<>(); for(PacketType t: PacketType.values()) if(t.handle()!=null && t.handle().getSide().name().equalsIgnoreCase("CLIENT")) s.add(t); return s;
  }
  public static Set<PacketType> getServerPacketTypes() {
    Set<PacketType> s=new LinkedHashSet<>(); for(PacketType t: PacketType.values()) if(t.handle()!=null && t.handle().getSide().name().equalsIgnoreCase("SERVER")) s.add(t); return s;
  }
}

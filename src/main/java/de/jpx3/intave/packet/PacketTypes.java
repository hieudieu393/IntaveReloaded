package de.jpx3.intave.packet;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;

public class PacketTypes {
  private final static PacketTypeCommon cteType;

  static {
    PacketTypeCommon clientTickEndType;
    try {
      clientTickEndType = PacketType.Play.Client.CLIENT_TICK_END;
    } catch (NoSuchFieldError error) {
      clientTickEndType = null;
    }
    cteType = clientTickEndType;
  }

  public static boolean isClientEndTick(PacketTypeCommon type) {
    return type != null && type.equals(cteType);
  }
}

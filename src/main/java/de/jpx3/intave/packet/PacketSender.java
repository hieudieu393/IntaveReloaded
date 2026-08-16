package de.jpx3.intave.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import de.jpx3.intave.packet.nativeapi.events.NativePacket;
import org.bukkit.entity.Player;

public final class PacketSender {
  private static final PlayerManager PLAYER_MANAGER = PacketEvents.getAPI().getPlayerManager();

  private PacketSender() {
  }

  public static void sendServerPacket(Player receiver, PacketWrapper<?> packet) {
    PLAYER_MANAGER.sendPacket(receiver, packet);
  }

  public static void sendServerPacketWithoutEvent(Player receiver, PacketWrapper<?> packet) {
    PLAYER_MANAGER.sendPacketSilently(receiver, packet);
  }

  public static void receiveClientPacketFrom(Player receiver, PacketWrapper<?> packet) {
    PLAYER_MANAGER.receivePacket(receiver, packet);
  }

  public static void sendServerPacket(Player receiver, NativePacket packet) {
    if (packet != null && packet.wrapper() != null) PLAYER_MANAGER.sendPacket(receiver, packet.wrapper());
  }

  public static void sendServerPacketWithoutEvent(Player receiver, NativePacket packet) {
    if (packet != null && packet.wrapper() != null) PLAYER_MANAGER.sendPacketSilently(receiver, packet.wrapper());
  }

  public static void receiveClientPacketFrom(Player receiver, NativePacket packet) {
    if (packet != null && packet.wrapper() != null) PLAYER_MANAGER.receivePacket(receiver, packet.wrapper());
  }

  public static void sendServerPacket(Player receiver, Object byteBuf) {
    PLAYER_MANAGER.sendPacket(receiver, byteBuf);
  }

  public static void sendServerPacketWithoutEvent(Player receiver, Object byteBuf) {
    PLAYER_MANAGER.sendPacketSilently(receiver, byteBuf);
  }

  public static void receiveClientPacketFrom(Player receiver, Object byteBuf) {
    PLAYER_MANAGER.receivePacket(receiver, byteBuf);
  }
}

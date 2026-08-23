package de.jpx3.intave.player;

import com.github.retrooper.packetevents.protocol.chat.ChatTypes;
import com.github.retrooper.packetevents.protocol.chat.message.ChatMessageLegacy;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerActionBar;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChatMessage;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.packet.PacketSender;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class ActionBar {
  private static final boolean DEDICATED_ACTION_BAR_PACKET = MinecraftVersions.VER1_17_0.atOrAbove();
  private static final boolean SERVER_EXISTS = Bukkit.getServer() != null;

  private ActionBar() {
  }

  public static void sendActionBar(Player player, String message) {
    if (!SERVER_EXISTS) {
      return;
    }

    Component component = Component.text(message);
    if (DEDICATED_ACTION_BAR_PACKET) {
      PacketSender.sendServerPacketWithoutEvent(player, new WrapperPlayServerActionBar(component));
    } else {
      PacketSender.sendServerPacketWithoutEvent(
        player,
        new WrapperPlayServerChatMessage(new ChatMessageLegacy(component, ChatTypes.GAME_INFO))
      );
    }
  }
}

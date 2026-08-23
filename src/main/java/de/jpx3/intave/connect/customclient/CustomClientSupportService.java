package de.jpx3.intave.connect.customclient;

import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPluginMessage;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.EventProcessor;
import de.jpx3.intave.connect.sibyl.LabyModChannelHelper;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.PacketSender;
import de.jpx3.intave.packet.reader.PayloadInReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ConnectionMetadata;
import io.netty.buffer.ByteBuf;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.CUSTOM_PAYLOAD_IN;

public final class CustomClientSupportService implements EventProcessor {
  private static final JsonParser jsonParser = new JsonParser();
  private final IntavePlugin plugin;

  public CustomClientSupportService(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  public void setup() {
    try {
      Bukkit.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "intave:customclient");
      Bukkit.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "minecraft:intave");
    } catch (Exception exception) {
//      IntaveLogger.logger().info("Failed to register output channel: " + exception.getClass().getSimpleName());
    }
    Modules.linker().packetEvents().linkSubscriptionsIn(this);
  }

  @PacketSubscription(
    packetsIn = {
      CUSTOM_PAYLOAD_IN
    }
  )
  public void receivePayloadPacket(Player player, PayloadInReader reader) {
    String tag = reader.tag();
    if (!tag.equalsIgnoreCase("intave")) {
      return;
    }
    ByteBuf bytes = reader.readBytes();
    try {
      bytes.markReaderIndex();
      String messageKey = LabyModChannelHelper.readString(bytes, 100);
      if (messageKey.equalsIgnoreCase("clientconfig")) {
        User user = UserRepository.userOf(player);
        ConnectionMetadata connectionData = user.meta().connection();
        if (System.currentTimeMillis() - connectionData.lastCCCInfoMessageSent > 4000) {
          IntaveLogger.logger().info(player.getName() + " has sent a custom client configuration (client has special Intave support)");
          connectionData.lastCCCInfoMessageSent = System.currentTimeMillis();
        }
        String messageContent = LabyModChannelHelper.readString(bytes, 32767);
        JsonElement jsonElement = jsonParser.parse(messageContent);
        CustomClientSupportConfig customClientSupportConfig = CustomClientSupportConfig.createFrom(jsonElement);
        user.setCustomClientSupport(customClientSupportConfig);
        sendCustomDataPacket(player, "clientconfig", "received", "minecraft", "intave");
        sendCustomDataPacket(player, "clientconfig", "received", "intave", "customclient");
      }
    } catch (RuntimeException exception) {
      exception.printStackTrace();
      Synchronizer.synchronize(() -> player.kickPlayer("Invalid Intave client support payload packet"));
    } finally {
      bytes.resetReaderIndex();
    }
  }

  private void sendCustomDataPacket(Player player, String channel, String data, String prefix, String key) {
    byte[] payload = LabyModChannelHelper.getBytesToSend(channel, data);
    WrapperPlayServerPluginMessage packet = new WrapperPlayServerPluginMessage(prefix + ":" + key, payload);
    Synchronizer.synchronize(() -> PacketSender.sendServerPacket(player, packet));
  }
}

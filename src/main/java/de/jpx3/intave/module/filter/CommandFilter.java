package de.jpx3.intave.module.filter;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatCommand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatCommandUnsigned;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatMessage;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientTabComplete;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTabComplete;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.permission.BukkitPermissionCheck;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.CHAT_IN;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.TAB_COMPLETE_IN;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.TAB_COMPLETE_OUT;

public final class CommandFilter extends Filter {
  private final boolean separateEnable;
  private final boolean disabled;
  private final Map<String, String> redirects = new HashMap<>();

  public CommandFilter(IntavePlugin plugin) {
    super("command");
    separateEnable = plugin.settings().getBoolean("command.hide", true);
    disabled = plugin.settings().getBoolean("command.fix-tab-kicks", false);
    ConfigurationSection reroute = plugin.settings().getConfigurationSection("command.reroute");
    if (reroute != null) reroute.getKeys(false).forEach(key -> redirects.put(key, plugin.settings().getString("command.reroute." + key)));
  }

  @PacketSubscription(packetsIn = {CHAT_IN, TAB_COMPLETE_IN})
  public void receiveChatPacket(ProtocolPacketEvent event) {
    if (!(event instanceof PacketReceiveEvent)) return;
    PacketReceiveEvent receiveEvent = (PacketReceiveEvent) event;
    String message = inboundText(receiveEvent);
    if (message == null) return;
    Player player = event.getPlayer();
    String trimmedMessage = message.trim().toLowerCase();
    for (Map.Entry<String, String> entry : redirects.entrySet()) {
      if (trimmedMessage.startsWith(entry.getKey())) {
        String redirect = entry.getValue();
        if (redirect == null || redirect.toLowerCase().contains("root")) continue;
        trimmedMessage = redirect + trimmedMessage.substring(entry.getKey().length());
        writeInboundText(receiveEvent, trimmedMessage);
        trimmedMessage = trimmedMessage.trim().toLowerCase();
      }
    }
    boolean permitted = BukkitPermissionCheck.permissionCheck(player, "intave.command");
    if ((trimmedMessage.startsWith("/iac") || trimmedMessage.startsWith("/intave")) && !permitted) {
      writeInboundText(receiveEvent, "/intavecommandforward");
    }
  }

  private static String inboundText(PacketReceiveEvent event) {
    PacketTypeCommon type = event.getPacketType();
    if (type == PacketType.Play.Client.CHAT_MESSAGE) return new WrapperPlayClientChatMessage(event).getMessage();
    if (type == PacketType.Play.Client.CHAT_COMMAND) return "/" + new WrapperPlayClientChatCommand(event).getCommand();
    if (type == PacketType.Play.Client.CHAT_COMMAND_UNSIGNED) return "/" + new WrapperPlayClientChatCommandUnsigned(event).getCommand();
    if (type == PacketType.Play.Client.TAB_COMPLETE) return new WrapperPlayClientTabComplete(event).getText();
    return null;
  }

  private static void writeInboundText(PacketReceiveEvent event, String value) {
    PacketTypeCommon type = event.getPacketType();
    if (type == PacketType.Play.Client.CHAT_MESSAGE) new WrapperPlayClientChatMessage(event).setMessage(value);
    else if (type == PacketType.Play.Client.CHAT_COMMAND) new WrapperPlayClientChatCommand(event).setCommand(stripSlash(value));
    else if (type == PacketType.Play.Client.CHAT_COMMAND_UNSIGNED) new WrapperPlayClientChatCommandUnsigned(event).setCommand(stripSlash(value));
    else if (type == PacketType.Play.Client.TAB_COMPLETE) new WrapperPlayClientTabComplete(event).setText(value);
  }

  private static String stripSlash(String value) {
    return value.startsWith("/") ? value.substring(1) : value;
  }

  @PacketSubscription(packetsOut = TAB_COMPLETE_OUT)
  public void receiveTabComplete(ProtocolPacketEvent event) {
    if (!(event instanceof PacketSendEvent)) return;
    Player player = event.getPlayer();
    if (BukkitPermissionCheck.permissionCheck(player, "intave.command")) return;
    WrapperPlayServerTabComplete wrapper = new WrapperPlayServerTabComplete((PacketSendEvent) event);
    List<WrapperPlayServerTabComplete.CommandMatch> filtered = new ArrayList<>();
    for (WrapperPlayServerTabComplete.CommandMatch match : wrapper.getCommandMatches()) {
      String text = match.getText();
      if (!text.contains("/intave") && !text.contains("/iac")) filtered.add(match);
    }
    if (filtered.size() != wrapper.getCommandMatches().size()) wrapper.setCommandMatches(filtered);
  }

  @Override
  protected boolean enabled() {
    return (super.enabled() || separateEnable) && !disabled;
  }
}

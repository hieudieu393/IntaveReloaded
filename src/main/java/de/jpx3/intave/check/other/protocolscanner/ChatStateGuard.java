package de.jpx3.intave.check.other.protocolscanner;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.common.client.WrapperCommonClientSettings;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatCommand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatCommandUnsigned;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatMessage;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientSettings;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.other.ProtocolScanner;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.CHAT_COMMAND;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.CHAT_COMMAND_UNSIGNED;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.CHAT_IN;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.CHAT_MESSAGE;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.SETTINGS;

public final class ChatStateGuard extends MetaCheckPart<ProtocolScanner, ChatStateGuard.Meta> {
  public ChatStateGuard(ProtocolScanner parentCheck) {
    super(parentCheck, Meta.class);
  }

  @PacketSubscription(packetsIn = SETTINGS, ignoreCancelled = false)
  public void receiveSettings(ProtocolPacketEvent event) {
    if (!(event instanceof PacketReceiveEvent)) return;
    WrapperPlayClientSettings wrapper = new WrapperPlayClientSettings((PacketReceiveEvent) event);
    metaOf(userOf(event.getPlayer())).chatHidden =
      wrapper.getChatVisibility() == WrapperCommonClientSettings.ChatVisibility.HIDDEN;
  }

  @PacketSubscription(
    packetsIn = {CHAT_IN, CHAT_MESSAGE, CHAT_COMMAND, CHAT_COMMAND_UNSIGNED},
    ignoreCancelled = false
  )
  public void receiveChat(ProtocolPacketEvent event) {
    if (!(event instanceof PacketReceiveEvent)) return;

    PacketReceiveEvent receive = (PacketReceiveEvent) event;
    String text;
    boolean command;
    try {
      if (event.getPacketType() == PacketType.Play.Client.CHAT_COMMAND_UNSIGNED) {
        text = new WrapperPlayClientChatCommandUnsigned(receive).getCommand();
        command = true;
      } else if (event.getPacketType() == PacketType.Play.Client.CHAT_COMMAND) {
        text = new WrapperPlayClientChatCommand(receive).getCommand();
        command = true;
      } else {
        text = new WrapperPlayClientChatMessage(receive).getMessage();
        command = false;
      }
    } catch (Throwable ignored) {
      return;
    }

    User user = userOf(event.getPlayer());
    String reason = null;
    if (text == null || text.isEmpty()) {
      reason = command ? "empty command" : "empty chat message";
    } else if (!text.trim().equals(text)) {
      reason = (command ? "command" : "message") + " has leading/trailing whitespace";
    } else if (!command && text.startsWith("/") && user.protocolVersion() >= 759) {
      reason = "slash command sent as chat message";
    }

    Meta meta = metaOf(user);
    if (reason == null && meta.chatHidden) reason = "chat packet while visibility=HIDDEN";
    if (reason == null) return;

    event.setCancelled(true);
    flag(user, reason, command ? "command" : "message", 10.0D);
  }

  private void flag(User user, String reason, String kind, double vl) {
    Violation violation = Violation.builderFor(ProtocolScanner.class)
      .forPlayer(user.player())
      .withCheckName("BadPackets")
      .withMessage("sent an invalid chat packet")
      .withDetails(kind + ": " + reason)
      .withVL(vl)
      .build();
    Modules.violationProcessor().processViolation(violation);
  }

  public static final class Meta extends CheckCustomMetadata {
    private boolean chatHidden;
  }
}

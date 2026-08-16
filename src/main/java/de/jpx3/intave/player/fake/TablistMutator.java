package de.jpx3.intave.player.fake;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import de.jpx3.intave.packet.nativeapi.PacketRuntime;
import de.jpx3.intave.packet.nativeapi.PacketRuntimeManager;
import de.jpx3.intave.packet.nativeapi.events.NativePacket;
import de.jpx3.intave.packet.nativeapi.wrappers.EnumWrappers;
import de.jpx3.intave.packet.nativeapi.wrappers.PlayerInfoData;
import de.jpx3.intave.packet.nativeapi.wrappers.WrappedChatComponent;
import de.jpx3.intave.packet.nativeapi.wrappers.WrappedGameProfile;
import de.jpx3.intave.packet.PacketSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class TablistMutator {
  private static final PacketRuntimeManager protocolManager = PacketRuntime.getPacketRuntimeManager();

  public static void addToTabList(
    Player player,
    WrappedGameProfile wrappedGameProfile,
    String tabListName
  ) {
    WrappedChatComponent wrappedChatComponent = WrappedChatComponent.fromText(tabListName);
    addToTabList(player, wrappedGameProfile, wrappedChatComponent);
  }

  private static void addToTabList(
    Player player,
    WrappedGameProfile profile,
    WrappedChatComponent wrappedChatComponent
  ) {
    NativePacket packet = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO_UPDATE);
    PlayerInfoData playerInfoData = new PlayerInfoData(
      profile, ThreadLocalRandom.current().nextInt(20, 200),
      EnumWrappers.NativeGameMode.SURVIVAL,
      wrappedChatComponent
    );
    List<PlayerInfoData> playerInformationList = packet.getPlayerInfoDataLists().readSafely(0);
    playerInformationList.add(playerInfoData);
    packet.getPlayerInfoAction().writeSafely(0, EnumWrappers.PlayerInfoAction.ADD_PLAYER);
    packet.getPlayerInfoDataLists().writeSafely(0, playerInformationList);
    PacketSender.sendServerPacket(player, packet);
  }

  public static void removeFromTabList(
    Player player,
    WrappedGameProfile profile
  ) {
    NativePacket packet = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO_UPDATE);
    WrappedChatComponent wrappedChatComponent = WrappedChatComponent.fromText(profile.getName());
    PlayerInfoData playerInfoData = new PlayerInfoData(
      profile, ThreadLocalRandom.current().nextInt(20, 200),
      EnumWrappers.NativeGameMode.SURVIVAL,
      wrappedChatComponent
    );
    List<PlayerInfoData> playerInformationList = packet.getPlayerInfoDataLists().readSafely(0);
    playerInformationList.add(playerInfoData);
    packet.getPlayerInfoAction().writeSafely(0, EnumWrappers.PlayerInfoAction.REMOVE_PLAYER);
    packet.getPlayerInfoDataLists().writeSafely(0, playerInformationList);
    PacketSender.sendServerPacket(player, packet);
  }
}

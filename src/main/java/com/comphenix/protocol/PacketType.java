package com.comphenix.protocol;

import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import java.util.*;

/** Legacy Intave packet identifier backed by PacketEvents packet types. */
public final class PacketType {
  private static final List<PacketType> VALUES = new ArrayList<>();
  private final PacketTypeCommon handle;
  private final String legacyName;
  private final Protocol protocol;

  private PacketType(String legacyName, Protocol protocol, boolean client, String... aliases) {
    this.legacyName = legacyName;
    this.protocol = protocol;
    this.handle = resolve(client, aliases);
    VALUES.add(this);
  }

  private static PacketTypeCommon resolve(boolean client, String... aliases) {
    for (String alias : aliases) {
      try {
        return client
          ? com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.valueOf(alias)
          : com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Server.valueOf(alias);
      } catch (IllegalArgumentException ignored) {}
    }
    return null;
  }

  public static PacketType fromHandle(PacketTypeCommon handle) {
    if (handle == null) return null;
    for (PacketType value : VALUES) if (value.handle == handle) return value;
    String name = handle.getName();
    for (PacketType value : VALUES) {
      if (value.legacyName.equalsIgnoreCase(name) || Arrays.stream(value.aliases()).anyMatch(name::equalsIgnoreCase)) return value;
    }
    boolean client = handle.getSide().name().equalsIgnoreCase("CLIENT");
    return new PacketType(name, Protocol.PLAY, client, name);
  }

  private String[] aliases() { return new String[] { handle == null ? legacyName : handle.getName() }; }
  public PacketTypeCommon handle() { return handle; }
  public String name() { return legacyName; }
  public Protocol getProtocol() { return protocol; }
  public boolean isSupported() { return handle != null; }
  public Class<?> getPacketClass() { return handle == null ? Object.class : (handle.getWrapperClass() == null ? Object.class : handle.getWrapperClass()); }

  public static Collection<PacketType> fromName(String name) {
    List<PacketType> out = new ArrayList<>();
    for (PacketType value : VALUES) {
      if (value.legacyName.equalsIgnoreCase(name) || (value.handle != null && value.handle.getName().equalsIgnoreCase(name))) out.add(value);
    }
    return out;
  }

  public static PacketType[] values() { return VALUES.toArray(new PacketType[0]); }
  @Override public String toString() { return legacyName; }

  public enum Protocol { PLAY, LOGIN, STATUS, HANDSHAKING, CONFIGURATION }

  private static PacketType c(String legacy, String... aliases) { return new PacketType(legacy, Protocol.PLAY, true, aliases); }
  private static PacketType s(String legacy, String... aliases) { return new PacketType(legacy, Protocol.PLAY, false, aliases); }

  public static final class Play {
    public static final class Client {
      public static final PacketType ARM_ANIMATION = c("ARM_ANIMATION", "ANIMATION", "ARM_ANIMATION");
      public static final PacketType BLOCK_DIG = c("BLOCK_DIG", "PLAYER_DIGGING", "BLOCK_DIG");
      public static final PacketType BLOCK_PLACE = c("BLOCK_PLACE", "PLAYER_BLOCK_PLACEMENT", "BLOCK_PLACE");
      public static final PacketType CHAT = c("CHAT", "CHAT_MESSAGE", "CHAT");
      public static final PacketType CLIENT_COMMAND = c("CLIENT_COMMAND", "CLIENT_STATUS", "CLIENT_COMMAND");
      public static final PacketType CLIENT_TICK_END = c("CLIENT_TICK_END", "CLIENT_TICK_END");
      public static final PacketType CLOSE_WINDOW = c("CLOSE_WINDOW", "CLOSE_WINDOW");
      public static final PacketType CUSTOM_PAYLOAD = c("CUSTOM_PAYLOAD", "PLUGIN_MESSAGE", "CUSTOM_PAYLOAD");
      public static final PacketType FLYING = c("FLYING", "PLAYER_FLYING", "FLYING");
      public static final PacketType LOOK = c("LOOK", "PLAYER_ROTATION", "LOOK");
      public static final PacketType POSITION = c("POSITION", "PLAYER_POSITION", "POSITION");
      public static final PacketType POSITION_LOOK = c("POSITION_LOOK", "PLAYER_POSITION_AND_ROTATION", "POSITION_LOOK");
      public static final PacketType TAB_COMPLETE = c("TAB_COMPLETE", "TAB_COMPLETE");
      public static final PacketType USE_ENTITY = c("USE_ENTITY", "INTERACT_ENTITY", "USE_ENTITY");
      public static final PacketType VEHICLE_MOVE = c("VEHICLE_MOVE", "VEHICLE_MOVE");
      public static final PacketType WINDOW_CLICK = c("WINDOW_CLICK", "CLICK_WINDOW", "WINDOW_CLICK");
    }
    public static final class Server {
      public static final PacketType ANIMATION = s("ANIMATION", "ENTITY_ANIMATION", "ANIMATION");
      public static final PacketType ATTACH_ENTITY = s("ATTACH_ENTITY", "ATTACH_ENTITY");
      public static final PacketType BLOCK_CHANGE = s("BLOCK_CHANGE", "BLOCK_CHANGE");
      public static final PacketType BLOCK_CHANGED_ACK = s("BLOCK_CHANGED_ACK", "ACKNOWLEDGE_BLOCK_CHANGES", "BLOCK_CHANGED_ACK");
      public static final PacketType CHAT = s("CHAT", "CHAT_MESSAGE", "SYSTEM_CHAT_MESSAGE", "CHAT");
      public static final PacketType CLOSE_WINDOW = s("CLOSE_WINDOW", "CLOSE_WINDOW");
      public static final PacketType CUSTOM_PAYLOAD = s("CUSTOM_PAYLOAD", "PLUGIN_MESSAGE", "CUSTOM_PAYLOAD");
      public static final PacketType ENTITY_DESTROY = s("ENTITY_DESTROY", "DESTROY_ENTITIES", "ENTITY_DESTROY");
      public static final PacketType ENTITY_EQUIPMENT = s("ENTITY_EQUIPMENT", "ENTITY_EQUIPMENT");
      public static final PacketType ENTITY_HEAD_ROTATION = s("ENTITY_HEAD_ROTATION", "ENTITY_HEAD_LOOK", "ENTITY_HEAD_ROTATION");
      public static final PacketType ENTITY_LOOK = s("ENTITY_LOOK", "ENTITY_ROTATION", "ENTITY_LOOK");
      public static final PacketType ENTITY_METADATA = s("ENTITY_METADATA", "ENTITY_METADATA");
      public static final PacketType ENTITY_STATUS = s("ENTITY_STATUS", "ENTITY_STATUS");
      public static final PacketType ENTITY_TELEPORT = s("ENTITY_TELEPORT", "ENTITY_TELEPORT");
      public static final PacketType ENTITY_VELOCITY = s("ENTITY_VELOCITY", "ENTITY_VELOCITY");
      public static final PacketType EXPERIENCE = s("EXPERIENCE", "SET_EXPERIENCE", "EXPERIENCE");
      public static final PacketType EXPLOSION = s("EXPLOSION", "EXPLOSION");
      public static final PacketType INITIALIZE_BORDER = s("INITIALIZE_BORDER", "INITIALIZE_WORLD_BORDER", "INITIALIZE_BORDER");
      public static final PacketType LOGIN = s("LOGIN", "JOIN_GAME", "LOGIN");
      public static final PacketType MAP_CHUNK = s("MAP_CHUNK", "CHUNK_DATA", "MAP_CHUNK");
      public static final PacketType MAP_CHUNK_BULK = s("MAP_CHUNK_BULK", "CHUNK_DATA_BULK", "MAP_CHUNK_BULK");
      public static final PacketType MOUNT = s("MOUNT", "SET_PASSENGERS", "MOUNT");
      public static final PacketType MULTI_BLOCK_CHANGE = s("MULTI_BLOCK_CHANGE", "MULTI_BLOCK_CHANGE");
      public static final PacketType NAMED_ENTITY_SPAWN = s("NAMED_ENTITY_SPAWN", "SPAWN_PLAYER", "NAMED_ENTITY_SPAWN");
      public static final PacketType NAMED_SOUND_EFFECT = s("NAMED_SOUND_EFFECT", "SOUND_EFFECT", "NAMED_SOUND_EFFECT");
      public static final PacketType OPEN_WINDOW = s("OPEN_WINDOW", "OPEN_WINDOW");
      public static final PacketType PING = s("PING", "PING");
      public static final PacketType PLAYER_INFO = s("PLAYER_INFO", "PLAYER_INFO", "PLAYER_INFO_UPDATE");
      public static final PacketType PLAYER_INFO_REMOVE = s("PLAYER_INFO_REMOVE", "PLAYER_INFO_REMOVE");
      public static final PacketType REL_ENTITY_MOVE = s("REL_ENTITY_MOVE", "ENTITY_RELATIVE_MOVE", "REL_ENTITY_MOVE");
      public static final PacketType REL_ENTITY_MOVE_LOOK = s("REL_ENTITY_MOVE_LOOK", "ENTITY_RELATIVE_MOVE_AND_ROTATION", "REL_ENTITY_MOVE_LOOK");
      public static final PacketType SCOREBOARD_TEAM = s("SCOREBOARD_TEAM", "TEAMS", "SCOREBOARD_TEAM");
      public static final PacketType SET_ACTION_BAR_TEXT = s("SET_ACTION_BAR_TEXT", "ACTION_BAR", "SET_ACTION_BAR_TEXT");
      public static final PacketType SET_BORDER_CENTER = s("SET_BORDER_CENTER", "WORLD_BORDER_CENTER", "SET_BORDER_CENTER");
      public static final PacketType SET_BORDER_LERP_SIZE = s("SET_BORDER_LERP_SIZE", "WORLD_BORDER_LERP_SIZE", "SET_BORDER_LERP_SIZE");
      public static final PacketType SET_BORDER_SIZE = s("SET_BORDER_SIZE", "WORLD_BORDER_SIZE", "SET_BORDER_SIZE");
      public static final PacketType SET_BORDER_WARNING_DELAY = s("SET_BORDER_WARNING_DELAY", "WORLD_BORDER_WARNING_DELAY", "SET_BORDER_WARNING_DELAY");
      public static final PacketType SET_BORDER_WARNING_DISTANCE = s("SET_BORDER_WARNING_DISTANCE", "WORLD_BORDER_WARNING_REACH", "SET_BORDER_WARNING_DISTANCE");
      public static final PacketType SPAWN_ENTITY = s("SPAWN_ENTITY", "SPAWN_ENTITY");
      public static final PacketType SPAWN_ENTITY_LIVING = s("SPAWN_ENTITY_LIVING", "SPAWN_LIVING_ENTITY", "SPAWN_ENTITY_LIVING");
      public static final PacketType STORE_COOKIE = s("STORE_COOKIE", "STORE_COOKIE");
      public static final PacketType TAB_COMPLETE = s("TAB_COMPLETE", "TAB_COMPLETE");
      public static final PacketType TRANSACTION = s("TRANSACTION", "WINDOW_CONFIRMATION", "TRANSACTION");
      public static final PacketType UPDATE_ATTRIBUTES = s("UPDATE_ATTRIBUTES", "UPDATE_ATTRIBUTES");
      public static final PacketType UPDATE_HEALTH = s("UPDATE_HEALTH", "UPDATE_HEALTH");
      public static final PacketType WINDOW_DATA = s("WINDOW_DATA", "WINDOW_PROPERTY", "WINDOW_DATA");
      public static final PacketType WINDOW_ITEMS = s("WINDOW_ITEMS", "WINDOW_ITEMS");
      public static final PacketType WORLD_BORDER = s("WORLD_BORDER", "WORLD_BORDER");
    }
  }
}

package de.jpx3.intave.packet.nativeapi;

import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.packettype.ServerBoundPacket;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/** Legacy Intave packet identifier backed entirely by PacketEvents packet types. */
public final class PacketType {
  private static final List<PacketType> VALUES = new CopyOnWriteArrayList<>();
  private static volatile boolean builtInsLoaded;

  private final PacketTypeCommon handle;
  private final String legacyName;
  private final Protocol protocol;

  private PacketType(String legacyName, Protocol protocol, boolean client, String... aliases) {
    this(legacyName, protocol, resolveFirst(client, aliases));
  }

  private PacketType(String legacyName, Protocol protocol, PacketTypeCommon handle) {
    this.legacyName = legacyName;
    this.protocol = protocol;
    this.handle = handle;
    VALUES.add(this);
  }

  private static PacketTypeCommon resolveFirst(boolean client, String... aliases) {
    for (String alias : aliases) {
      PacketTypeCommon resolved = resolve(client, alias);
      if (resolved != null) return resolved;
    }
    return null;
  }

  private static PacketTypeCommon resolve(boolean client, String alias) {
    if (alias == null || alias.isEmpty() || "*".equals(alias)) return null;
    try {
      return client
        ? com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.valueOf(alias)
        : com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Server.valueOf(alias);
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  private static void ensureBuiltIns() {
    if (builtInsLoaded) return;
    synchronized (PacketType.class) {
      if (builtInsLoaded) return;
      Play.Client.bootstrap();
      Play.Server.bootstrap();
      builtInsLoaded = true;
    }
  }

  private static PacketType getOrCreate(String legacyName, boolean client, PacketTypeCommon handle) {
    if (handle == null) return null;
    for (PacketType value : VALUES) if (value.handle == handle) return value;
    synchronized (PacketType.class) {
      for (PacketType value : VALUES) if (value.handle == handle) return value;
      return new PacketType(legacyName, Protocol.PLAY, handle);
    }
  }

  private static void addResolved(Collection<PacketType> out, boolean client, String legacyName, String... aliases) {
    for (String alias : aliases) {
      PacketTypeCommon handle = resolve(client, alias);
      PacketType value = getOrCreate(legacyName, client, handle);
      if (value != null && !out.contains(value)) out.add(value);
    }
  }

  public static PacketType fromHandle(PacketTypeCommon handle) {
    if (handle == null) return null;
    ensureBuiltIns();
    for (PacketType value : VALUES) if (value.handle == handle) return value;
    boolean client = handle instanceof ServerBoundPacket;
    return getOrCreate(handle.getName(), client, handle);
  }

  public PacketTypeCommon handle() { return handle; }
  public String name() { return legacyName; }
  public Protocol getProtocol() { return protocol; }
  public boolean isSupported() { return handle != null; }
  public Class<?> getPacketClass() { return handle == null || handle.getWrapperClass() == null ? Object.class : handle.getWrapperClass(); }

  public static Collection<PacketType> fromName(String name) {
    ensureBuiltIns();
    List<PacketType> out = new ArrayList<>();
    for (PacketType value : VALUES) {
      if (value.legacyName.equalsIgnoreCase(name) || (value.handle != null && value.handle.getName().equalsIgnoreCase(name))) {
        if (!out.contains(value)) out.add(value);
      }
    }
    addResolved(out, true, name, aliasesForClient(name));
    addResolved(out, false, name, aliasesForServer(name));
    return out;
  }

  public static Collection<PacketType> clientValues() {
    ensureBuiltIns();
    List<PacketType> out = new ArrayList<>();
    for (com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client handle
      : com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.values()) {
      PacketType value = getOrCreate(handle.getName(), true, handle);
      if (value != null) out.add(value);
    }
    return out;
  }

  public static Collection<PacketType> serverValues() {
    ensureBuiltIns();
    List<PacketType> out = new ArrayList<>();
    for (com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Server handle
      : com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Server.values()) {
      PacketType value = getOrCreate(handle.getName(), false, handle);
      if (value != null) out.add(value);
    }
    return out;
  }

  public static PacketType[] values() {
    ensureBuiltIns();
    return VALUES.toArray(new PacketType[0]);
  }

  @Override public String toString() { return legacyName; }

  public enum Protocol { PLAY, LOGIN, STATUS, HANDSHAKING, CONFIGURATION }

  private static String[] aliasesForClient(String name) {
    switch (name.toUpperCase(Locale.ROOT)) {
      case "ABILITIES": return a("PLAYER_ABILITIES");
      case "ADVANCEMENTS": return a("ADVANCEMENT_TAB");
      case "ATTACK": return a("ATTACK", "INTERACT_ENTITY");
      case "ARM_ANIMATION": return a("ANIMATION");
      case "AUTO_RECIPE": return a("CRAFT_RECIPE_REQUEST");
      case "BEACON": return a("SET_BEACON_EFFECT");
      case "BLOCK_DIG": return a("PLAYER_DIGGING");
      case "BLOCK_PLACE": return a("PLAYER_BLOCK_PLACEMENT");
      case "BOAT_MOVE": return a("STEER_BOAT");
      case "B_EDIT": return a("EDIT_BOOK");
      case "CHAT": return a("CHAT_MESSAGE", "CHAT_COMMAND", "CHAT_COMMAND_UNSIGNED");
      case "CLIENT_COMMAND": return a("CLIENT_STATUS");
      case "CLIENT_TICK_END": return a("CLIENT_TICK_END");
      case "CLOSE_WINDOW": return a("CLOSE_WINDOW");
      case "CUSTOM_PAYLOAD": return a("PLUGIN_MESSAGE");
      case "DIFFICULTY_CHANGE": return a("SET_DIFFICULTY");
      case "DIFFICULTY_LOCK": return a("LOCK_DIFFICULTY");
      case "ENCHANT_ITEM": return a("CLICK_WINDOW_BUTTON");
      case "ENTITY_ACTION": return a("ENTITY_ACTION");
      case "ENTITY_NBT_QUERY": return a("QUERY_ENTITY_NBT");
      case "FLYING": return a("PLAYER_FLYING");
      case "HELD_ITEM_SLOT": return a("HELD_ITEM_CHANGE");
      case "ITEM_NAME": return a("NAME_ITEM");
      case "JIGSAW_GENERATE": return a("GENERATE_STRUCTURE");
      case "KEEP_ALIVE": return a("KEEP_ALIVE");
      case "LOOK": return a("PLAYER_ROTATION");
      case "PICK_ITEM": return a("PICK_ITEM", "PICK_ITEM_FROM_BLOCK", "PICK_ITEM_FROM_ENTITY");
      case "PONG": return a("PONG");
      case "POSITION": return a("PLAYER_POSITION");
      case "POSITION_LOOK": return a("PLAYER_POSITION_AND_ROTATION");
      case "RECIPE_DISPLAYED": return a("SET_DISPLAYED_RECIPE");
      case "RECIPE_SETTINGS": return a("SET_RECIPE_BOOK_STATE", "RECIPE_BOOK_DATA");
      case "RESOURCE_PACK_STATUS": return a("RESOURCE_PACK_STATUS");
      case "SETTINGS": return a("CLIENT_SETTINGS");
      case "SET_COMMAND_BLOCK": return a("UPDATE_COMMAND_BLOCK");
      case "SET_COMMAND_MINECART": return a("UPDATE_COMMAND_BLOCK_MINECART");
      case "SET_CREATIVE_SLOT": return a("CREATIVE_INVENTORY_ACTION");
      case "SET_JIGSAW": return a("UPDATE_JIGSAW_BLOCK");
      case "SPECTATE": return a("SPECTATE", "SPECTATE_ENTITY");
      case "STEER_VEHICLE": return a("STEER_VEHICLE", "PLAYER_INPUT");
      case "STRUCT": return a("UPDATE_STRUCTURE_BLOCK");
      case "TAB_COMPLETE": return a("TAB_COMPLETE");
      case "TELEPORT_ACCEPT": return a("TELEPORT_CONFIRM");
      case "TILE_NBT_QUERY": return a("QUERY_BLOCK_NBT");
      case "TRANSACTION": return a("WINDOW_CONFIRMATION");
      case "TR_SEL": return a("SELECT_TRADE");
      case "UPDATE_SIGN": return a("UPDATE_SIGN");
      case "USE_ENTITY": return a("INTERACT_ENTITY", "ATTACK");
      case "USE_ITEM": return a("USE_ITEM");
      case "USE_ITEM_ON": return a("PLAYER_BLOCK_PLACEMENT");
      case "VEHICLE_MOVE": return a("VEHICLE_MOVE");
      case "WINDOW_CLICK": return a("CLICK_WINDOW");
      default: return a(name);
    }
  }

  private static String[] aliasesForServer(String name) {
    switch (name.toUpperCase(Locale.ROOT)) {
      case "ABILITIES": return a("PLAYER_ABILITIES");
      case "ADVANCEMENTS": return a("UPDATE_ADVANCEMENTS");
      case "ANIMATION": return a("ENTITY_ANIMATION");
      case "ATTACH_ENTITY": return a("ATTACH_ENTITY");
      case "AUTO_RECIPE": return a("CRAFT_RECIPE_RESPONSE");
      case "BED": return a("USE_BED");
      case "BLOCK_ACTION": return a("BLOCK_ACTION");
      case "BLOCK_BREAK": return a("ACKNOWLEDGE_PLAYER_DIGGING");
      case "BLOCK_BREAK_ANIMATION": return a("BLOCK_BREAK_ANIMATION");
      case "BLOCK_CHANGE": return a("BLOCK_CHANGE");
      case "BLOCK_CHANGED_ACK": return a("ACKNOWLEDGE_BLOCK_CHANGES");
      case "BOSS": return a("BOSS_BAR");
      case "BUNDLE": return a("BUNDLE");
      case "CAMERA": return a("CAMERA");
      case "CHAT": return a("CHAT_MESSAGE", "SYSTEM_CHAT_MESSAGE", "DISGUISED_CHAT");
      case "CLOSE_WINDOW": return a("CLOSE_WINDOW");
      case "COLLECT": return a("COLLECT_ITEM");
      case "COMBAT_EVENT": return a("COMBAT_EVENT", "DEATH_COMBAT_EVENT", "END_COMBAT_EVENT", "ENTER_COMBAT_EVENT");
      case "COMMANDS": return a("DECLARE_COMMANDS");
      case "CRAFT_PROGRESS_BAR": return a("WINDOW_PROPERTY");
      case "CUSTOM_PAYLOAD": return a("PLUGIN_MESSAGE");
      case "CUSTOM_SOUND_EFFECT": return a("SOUND_EFFECT", "NAMED_SOUND_EFFECT");
      case "ENTITY": return a("ENTITY_MOVEMENT");
      case "ENTITY_DESTROY": return a("DESTROY_ENTITIES");
      case "ENTITY_EFFECT": return a("ENTITY_EFFECT");
      case "ENTITY_EQUIPMENT": return a("ENTITY_EQUIPMENT");
      case "ENTITY_HEAD_ROTATION": return a("ENTITY_HEAD_LOOK");
      case "ENTITY_LOOK": return a("ENTITY_ROTATION");
      case "ENTITY_METADATA": return a("ENTITY_METADATA");
      case "ENTITY_MOVE_LOOK": return a("ENTITY_RELATIVE_MOVE_AND_ROTATION");
      case "ENTITY_POSITION_SYNC": return a("ENTITY_POSITION_SYNC");
      case "ENTITY_SOUND": return a("ENTITY_SOUND_EFFECT");
      case "ENTITY_STATUS": return a("ENTITY_STATUS");
      case "ENTITY_TELEPORT": return a("ENTITY_TELEPORT");
      case "ENTITY_VELOCITY": return a("ENTITY_VELOCITY");
      case "EXPERIENCE": return a("SET_EXPERIENCE");
      case "EXPLOSION": return a("EXPLOSION");
      case "GAME_STATE_CHANGE": return a("CHANGE_GAME_STATE");
      case "HELD_ITEM_SLOT": return a("HELD_ITEM_CHANGE");
      case "INITIALIZE_BORDER": return a("INITIALIZE_WORLD_BORDER");
      case "KEEP_ALIVE": return a("KEEP_ALIVE");
      case "KICK_DISCONNECT": return a("DISCONNECT");
      case "LIGHT_UPDATE": return a("UPDATE_LIGHT");
      case "LOGIN": return a("JOIN_GAME");
      case "LOOK_AT": return a("FACE_PLAYER");
      case "MAP": return a("MAP_DATA");
      case "MAP_CHUNK": return a("CHUNK_DATA");
      case "MAP_CHUNK_BULK": return a("MAP_CHUNK_BULK");
      case "MOUNT": return a("SET_PASSENGERS");
      case "MULTI_BLOCK_CHANGE": return a("MULTI_BLOCK_CHANGE");
      case "NAMED_ENTITY_SPAWN": return a("SPAWN_PLAYER");
      case "NAMED_SOUND_EFFECT": return a("NAMED_SOUND_EFFECT");
      case "NBT_QUERY": return a("NBT_QUERY_RESPONSE");
      case "OPEN_BOOK": return a("OPEN_BOOK");
      case "OPEN_SIGN_EDITOR": return a("OPEN_SIGN_EDITOR");
      case "OPEN_SIGN_ENTITY": return a("OPEN_SIGN_EDITOR");
      case "OPEN_WINDOW": return a("OPEN_WINDOW");
      case "OPEN_WINDOW_HORSE": return a("OPEN_HORSE_WINDOW");
      case "OPEN_WINDOW_MERCHANT": return a("MERCHANT_OFFERS");
      case "PING": return a("PING");
      case "PLAYER_INFO": return a("PLAYER_INFO", "PLAYER_INFO_UPDATE");
      case "PLAYER_INFO_REMOVE": return a("PLAYER_INFO_REMOVE");
      case "PLAYER_LIST_HEADER_FOOTER": return a("PLAYER_LIST_HEADER_AND_FOOTER");
      case "POSITION": return a("PLAYER_POSITION_AND_LOOK");
      case "RECIPES": return a("DECLARE_RECIPES");
      case "RECIPE_UPDATE": return a("UNLOCK_RECIPES", "RECIPE_BOOK_ADD", "RECIPE_BOOK_REMOVE");
      case "REL_ENTITY_MOVE": return a("ENTITY_RELATIVE_MOVE");
      case "REL_ENTITY_MOVE_LOOK": return a("ENTITY_RELATIVE_MOVE_AND_ROTATION");
      case "REMOVE_ENTITY_EFFECT": return a("REMOVE_ENTITY_EFFECT");
      case "RESOURCE_PACK_SEND": return a("RESOURCE_PACK_SEND");
      case "RESPAWN": return a("RESPAWN");
      case "SCOREBOARD_DISPLAY_OBJECTIVE": return a("DISPLAY_SCOREBOARD");
      case "SCOREBOARD_OBJECTIVE": return a("SCOREBOARD_OBJECTIVE");
      case "SCOREBOARD_SCORE": return a("UPDATE_SCORE", "RESET_SCORE");
      case "SCOREBOARD_TEAM": return a("TEAMS");
      case "SELECT_ADVANCEMENT_TAB": return a("SELECT_ADVANCEMENTS_TAB");
      case "SERVER_DIFFICULTY": return a("SERVER_DIFFICULTY");
      case "SET_BORDER_CENTER": return a("WORLD_BORDER_CENTER");
      case "SET_BORDER_LERP_SIZE": return a("WORLD_BORDER_LERP_SIZE");
      case "SET_BORDER_SIZE": return a("WORLD_BORDER_SIZE");
      case "SET_BORDER_WARNING_DELAY": return a("WORLD_BORDER_WARNING_DELAY");
      case "SET_BORDER_WARNING_DISTANCE": return a("WORLD_BORDER_WARNING_REACH");
      case "SET_COMPRESSION": return a("SET_COMPRESSION");
      case "SET_COOLDOWN": return a("SET_COOLDOWN");
      case "SET_SLOT": return a("SET_SLOT");
      case "SPAWN_ENTITY": return a("SPAWN_ENTITY");
      case "SPAWN_ENTITY_EXPERIENCE_ORB": return a("SPAWN_EXPERIENCE_ORB");
      case "SPAWN_ENTITY_LIVING": return a("SPAWN_LIVING_ENTITY");
      case "SPAWN_ENTITY_PAINTING": return a("SPAWN_PAINTING");
      case "SPAWN_ENTITY_WEATHER": return a("SPAWN_WEATHER_ENTITY");
      case "SPAWN_POSITION": return a("SPAWN_POSITION");
      case "STATISTIC":
      case "STATISTICS": return a("STATISTICS");
      case "STOP_SOUND": return a("STOP_SOUND");
      case "TAB_COMPLETE": return a("TAB_COMPLETE");
      case "TAGS": return a("TAGS");
      case "TILE_ENTITY_DATA": return a("BLOCK_ENTITY_DATA");
      case "TITLE": return a("TITLE", "SET_TITLE_TEXT", "SET_TITLE_SUBTITLE", "SET_TITLE_TIMES");
      case "TRANSACTION": return a("WINDOW_CONFIRMATION");
      case "UNLOAD_CHUNK": return a("UNLOAD_CHUNK");
      case "UPDATE_ATTRIBUTES": return a("UPDATE_ATTRIBUTES");
      case "UPDATE_ENTITY_NBT": return a("UPDATE_ENTITY_NBT");
      case "UPDATE_HEALTH": return a("UPDATE_HEALTH");
      case "UPDATE_SIGN": return a("UPDATE_SIGN");
      case "UPDATE_TIME": return a("TIME_UPDATE");
      case "USE_BED": return a("USE_BED");
      case "VEHICLE_MOVE": return a("VEHICLE_MOVE");
      case "VIEW_CENTRE": return a("UPDATE_VIEW_POSITION");
      case "VIEW_DISTANCE": return a("UPDATE_VIEW_DISTANCE");
      case "WINDOW_DATA": return a("WINDOW_PROPERTY");
      case "WINDOW_ITEMS": return a("WINDOW_ITEMS");
      case "WORLD_BORDER": return a("WORLD_BORDER");
      case "WORLD_EVENT": return a("EFFECT");
      case "WORLD_PARTICLES": return a("PARTICLE");
      default: return a(name);
    }
  }

  private static String[] a(String... names) { return names; }
  private static PacketType c(String legacy, String... aliases) { return new PacketType(legacy, Protocol.PLAY, true, aliases); }
  private static PacketType s(String legacy, String... aliases) { return new PacketType(legacy, Protocol.PLAY, false, aliases); }

  public static final class Play {
    public static final class Client {
      public static final PacketType ARM_ANIMATION = c("ARM_ANIMATION", "ANIMATION");
      public static final PacketType BLOCK_DIG = c("BLOCK_DIG", "PLAYER_DIGGING");
      public static final PacketType BLOCK_PLACE = c("BLOCK_PLACE", "PLAYER_BLOCK_PLACEMENT");
      public static final PacketType CHAT = c("CHAT", "CHAT_MESSAGE");
      public static final PacketType CLIENT_COMMAND = c("CLIENT_COMMAND", "CLIENT_STATUS");
      public static final PacketType CLIENT_TICK_END = c("CLIENT_TICK_END", "CLIENT_TICK_END");
      public static final PacketType CLOSE_WINDOW = c("CLOSE_WINDOW", "CLOSE_WINDOW");
      public static final PacketType CUSTOM_PAYLOAD = c("CUSTOM_PAYLOAD", "PLUGIN_MESSAGE");
      public static final PacketType FLYING = c("FLYING", "PLAYER_FLYING");
      public static final PacketType LOOK = c("LOOK", "PLAYER_ROTATION");
      public static final PacketType POSITION = c("POSITION", "PLAYER_POSITION");
      public static final PacketType POSITION_LOOK = c("POSITION_LOOK", "PLAYER_POSITION_AND_ROTATION");
      public static final PacketType TAB_COMPLETE = c("TAB_COMPLETE", "TAB_COMPLETE");
      public static final PacketType USE_ENTITY = c("USE_ENTITY", "INTERACT_ENTITY");
      public static final PacketType VEHICLE_MOVE = c("VEHICLE_MOVE", "VEHICLE_MOVE");
      public static final PacketType WINDOW_CLICK = c("WINDOW_CLICK", "CLICK_WINDOW");
      private static void bootstrap() {}
    }
    public static final class Server {
      public static final PacketType ANIMATION = s("ANIMATION", "ENTITY_ANIMATION");
      public static final PacketType ATTACH_ENTITY = s("ATTACH_ENTITY", "ATTACH_ENTITY");
      public static final PacketType BLOCK_CHANGE = s("BLOCK_CHANGE", "BLOCK_CHANGE");
      public static final PacketType BLOCK_CHANGED_ACK = s("BLOCK_CHANGED_ACK", "ACKNOWLEDGE_BLOCK_CHANGES");
      public static final PacketType BUNDLE = s("BUNDLE", "BUNDLE");
      public static final PacketType CHAT = s("CHAT", "CHAT_MESSAGE", "SYSTEM_CHAT_MESSAGE");
      public static final PacketType CLOSE_WINDOW = s("CLOSE_WINDOW", "CLOSE_WINDOW");
      public static final PacketType CUSTOM_PAYLOAD = s("CUSTOM_PAYLOAD", "PLUGIN_MESSAGE");
      public static final PacketType ENTITY_DESTROY = s("ENTITY_DESTROY", "DESTROY_ENTITIES");
      public static final PacketType ENTITY_EQUIPMENT = s("ENTITY_EQUIPMENT", "ENTITY_EQUIPMENT");
      public static final PacketType ENTITY_HEAD_ROTATION = s("ENTITY_HEAD_ROTATION", "ENTITY_HEAD_LOOK");
      public static final PacketType ENTITY_LOOK = s("ENTITY_LOOK", "ENTITY_ROTATION");
      public static final PacketType ENTITY_METADATA = s("ENTITY_METADATA", "ENTITY_METADATA");
      public static final PacketType ENTITY_STATUS = s("ENTITY_STATUS", "ENTITY_STATUS");
      public static final PacketType ENTITY_TELEPORT = s("ENTITY_TELEPORT", "ENTITY_TELEPORT");
      public static final PacketType ENTITY_VELOCITY = s("ENTITY_VELOCITY", "ENTITY_VELOCITY");
      public static final PacketType EXPERIENCE = s("EXPERIENCE", "SET_EXPERIENCE");
      public static final PacketType EXPLOSION = s("EXPLOSION", "EXPLOSION");
      public static final PacketType INITIALIZE_BORDER = s("INITIALIZE_BORDER", "INITIALIZE_WORLD_BORDER");
      public static final PacketType KEEP_ALIVE = s("KEEP_ALIVE", "KEEP_ALIVE");
      public static final PacketType LOGIN = s("LOGIN", "JOIN_GAME");
      public static final PacketType MAP_CHUNK = s("MAP_CHUNK", "CHUNK_DATA");
      public static final PacketType MAP_CHUNK_BULK = s("MAP_CHUNK_BULK", "MAP_CHUNK_BULK");
      public static final PacketType MOUNT = s("MOUNT", "SET_PASSENGERS");
      public static final PacketType MULTI_BLOCK_CHANGE = s("MULTI_BLOCK_CHANGE", "MULTI_BLOCK_CHANGE");
      public static final PacketType NAMED_ENTITY_SPAWN = s("NAMED_ENTITY_SPAWN", "SPAWN_PLAYER");
      public static final PacketType NAMED_SOUND_EFFECT = s("NAMED_SOUND_EFFECT", "NAMED_SOUND_EFFECT");
      public static final PacketType OPEN_WINDOW = s("OPEN_WINDOW", "OPEN_WINDOW");
      public static final PacketType PING = s("PING", "PING");
      public static final PacketType PLAYER_INFO = s("PLAYER_INFO", "PLAYER_INFO", "PLAYER_INFO_UPDATE");
      public static final PacketType PLAYER_INFO_REMOVE = s("PLAYER_INFO_REMOVE", "PLAYER_INFO_REMOVE");
      public static final PacketType REL_ENTITY_MOVE = s("REL_ENTITY_MOVE", "ENTITY_RELATIVE_MOVE");
      public static final PacketType REL_ENTITY_MOVE_LOOK = s("REL_ENTITY_MOVE_LOOK", "ENTITY_RELATIVE_MOVE_AND_ROTATION");
      public static final PacketType SCOREBOARD_TEAM = s("SCOREBOARD_TEAM", "TEAMS");
      public static final PacketType SET_ACTION_BAR_TEXT = s("SET_ACTION_BAR_TEXT", "ACTION_BAR");
      public static final PacketType SET_BORDER_CENTER = s("SET_BORDER_CENTER", "WORLD_BORDER_CENTER");
      public static final PacketType SET_BORDER_LERP_SIZE = s("SET_BORDER_LERP_SIZE", "WORLD_BORDER_LERP_SIZE");
      public static final PacketType SET_BORDER_SIZE = s("SET_BORDER_SIZE", "WORLD_BORDER_SIZE");
      public static final PacketType SET_BORDER_WARNING_DELAY = s("SET_BORDER_WARNING_DELAY", "WORLD_BORDER_WARNING_DELAY");
      public static final PacketType SET_BORDER_WARNING_DISTANCE = s("SET_BORDER_WARNING_DISTANCE", "WORLD_BORDER_WARNING_REACH");
      public static final PacketType SPAWN_ENTITY = s("SPAWN_ENTITY", "SPAWN_ENTITY");
      public static final PacketType SPAWN_ENTITY_LIVING = s("SPAWN_ENTITY_LIVING", "SPAWN_LIVING_ENTITY");
      public static final PacketType STORE_COOKIE = s("STORE_COOKIE", "STORE_COOKIE");
      public static final PacketType TAB_COMPLETE = s("TAB_COMPLETE", "TAB_COMPLETE");
      public static final PacketType TRANSACTION = s("TRANSACTION", "WINDOW_CONFIRMATION");
      public static final PacketType UPDATE_ATTRIBUTES = s("UPDATE_ATTRIBUTES", "UPDATE_ATTRIBUTES");
      public static final PacketType UPDATE_HEALTH = s("UPDATE_HEALTH", "UPDATE_HEALTH");
      public static final PacketType WINDOW_DATA = s("WINDOW_DATA", "WINDOW_PROPERTY");
      public static final PacketType WINDOW_ITEMS = s("WINDOW_ITEMS", "WINDOW_ITEMS");
      public static final PacketType WORLD_BORDER = s("WORLD_BORDER", "WORLD_BORDER");
      private static void bootstrap() {}
    }
  }
}

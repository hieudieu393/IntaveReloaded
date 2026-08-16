package de.jpx3.intave.module.linker.packet;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Resolves Intave's stable packet ids directly to PacketEvents packet types. */
public final class PacketTypeResolver {
  private PacketTypeResolver() {}

  public static PacketTypeCommon[] client(PacketId.Client[] ids) {
    if (ids.length == 1 && "*".equals(ids[0].lookupName())) return PacketType.Play.Client.values();
    Set<PacketTypeCommon> out = new LinkedHashSet<>();
    for (PacketId.Client id : ids) addClient(out, id.lookupName());
    return out.toArray(new PacketTypeCommon[0]);
  }

  public static PacketTypeCommon[] server(PacketId.Server[] ids) {
    if (ids.length == 1 && "*".equals(ids[0].lookupName())) return PacketType.Play.Server.values();
    Set<PacketTypeCommon> out = new LinkedHashSet<>();
    for (PacketId.Server id : ids) addServer(out, id.lookupName());
    return out.toArray(new PacketTypeCommon[0]);
  }

  public static PacketTypeCommon firstClient(PacketId.Client id) {
    PacketTypeCommon[] types = client(new PacketId.Client[]{id});
    return types.length == 0 ? null : types[0];
  }

  public static PacketTypeCommon firstServer(PacketId.Server id) {
    PacketTypeCommon[] types = server(new PacketId.Server[]{id});
    return types.length == 0 ? null : types[0];
  }

  private static void addClient(Collection<PacketTypeCommon> out, String name) {
    for (String alias : clientAliases(name)) {
      PacketTypeCommon type = clientByName(alias);
      if (type != null) out.add(type);
    }
  }

  private static void addServer(Collection<PacketTypeCommon> out, String name) {
    for (String alias : serverAliases(name)) {
      PacketTypeCommon type = serverByName(alias);
      if (type != null) out.add(type);
    }
  }

  private static PacketTypeCommon clientByName(String name) {
    try { return PacketType.Play.Client.valueOf(name); }
    catch (IllegalArgumentException ignored) { return null; }
  }

  private static PacketTypeCommon serverByName(String name) {
    try { return PacketType.Play.Server.valueOf(name); }
    catch (IllegalArgumentException ignored) { return null; }
  }

  private static String[] clientAliases(String name) {
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
      case "CUSTOM_PAYLOAD": return a("PLUGIN_MESSAGE");
      case "DIFFICULTY_CHANGE": return a("SET_DIFFICULTY");
      case "DIFFICULTY_LOCK": return a("LOCK_DIFFICULTY");
      case "ENCHANT_ITEM": return a("CLICK_WINDOW_BUTTON");
      case "ENTITY_NBT_QUERY": return a("QUERY_ENTITY_NBT");
      case "FLYING": return a("PLAYER_FLYING");
      case "HELD_ITEM_SLOT": return a("HELD_ITEM_CHANGE");
      case "ITEM_NAME": return a("NAME_ITEM");
      case "JIGSAW_GENERATE": return a("GENERATE_STRUCTURE");
      case "LOOK": return a("PLAYER_ROTATION");
      case "PICK_ITEM": return a("PICK_ITEM", "PICK_ITEM_FROM_BLOCK", "PICK_ITEM_FROM_ENTITY");
      case "POSITION": return a("PLAYER_POSITION");
      case "POSITION_LOOK": return a("PLAYER_POSITION_AND_ROTATION");
      case "RECIPE_DISPLAYED": return a("SET_DISPLAYED_RECIPE");
      case "RECIPE_SETTINGS": return a("SET_RECIPE_BOOK_STATE", "RECIPE_BOOK_DATA");
      case "SETTINGS": return a("CLIENT_SETTINGS");
      case "SET_COMMAND_BLOCK": return a("UPDATE_COMMAND_BLOCK");
      case "SET_COMMAND_MINECART": return a("UPDATE_COMMAND_BLOCK_MINECART");
      case "SET_CREATIVE_SLOT": return a("CREATIVE_INVENTORY_ACTION");
      case "SET_JIGSAW": return a("UPDATE_JIGSAW_BLOCK");
      case "SPECTATE": return a("SPECTATE", "SPECTATE_ENTITY");
      case "STEER_VEHICLE": return a("STEER_VEHICLE", "PLAYER_INPUT");
      case "STRUCT": return a("UPDATE_STRUCTURE_BLOCK");
      case "TELEPORT_ACCEPT": return a("TELEPORT_CONFIRM");
      case "TILE_NBT_QUERY": return a("QUERY_BLOCK_NBT");
      case "TRANSACTION": return a("WINDOW_CONFIRMATION");
      case "TR_SEL": return a("SELECT_TRADE");
      case "USE_ENTITY": return a("INTERACT_ENTITY", "ATTACK");
      case "USE_ITEM_ON": return a("PLAYER_BLOCK_PLACEMENT");
      case "WINDOW_CLICK": return a("CLICK_WINDOW");
      default: return a(name);
    }
  }

  private static String[] serverAliases(String name) {
    switch (name.toUpperCase(Locale.ROOT)) {
      case "ABILITIES": return a("PLAYER_ABILITIES");
      case "ADVANCEMENTS": return a("UPDATE_ADVANCEMENTS");
      case "ANIMATION": return a("ENTITY_ANIMATION");
      case "AUTO_RECIPE": return a("CRAFT_RECIPE_RESPONSE");
      case "BED": return a("USE_BED");
      case "BLOCK_BREAK": return a("ACKNOWLEDGE_PLAYER_DIGGING");
      case "BLOCK_CHANGED_ACK": return a("ACKNOWLEDGE_BLOCK_CHANGES");
      case "BOSS": return a("BOSS_BAR");
      case "CHAT": return a("CHAT_MESSAGE", "SYSTEM_CHAT_MESSAGE", "DISGUISED_CHAT", "ACTION_BAR");
      case "COLLECT": return a("COLLECT_ITEM");
      case "COMBAT_EVENT": return a("COMBAT_EVENT", "DEATH_COMBAT_EVENT", "END_COMBAT_EVENT", "ENTER_COMBAT_EVENT");
      case "COMMANDS": return a("DECLARE_COMMANDS");
      case "CRAFT_PROGRESS_BAR": return a("WINDOW_PROPERTY");
      case "CUSTOM_PAYLOAD": return a("PLUGIN_MESSAGE");
      case "CUSTOM_SOUND_EFFECT": return a("SOUND_EFFECT", "NAMED_SOUND_EFFECT");
      case "ENTITY": return a("ENTITY_MOVEMENT");
      case "ENTITY_DESTROY": return a("DESTROY_ENTITIES");
      case "ENTITY_HEAD_ROTATION": return a("ENTITY_HEAD_LOOK");
      case "ENTITY_LOOK": return a("ENTITY_ROTATION");
      case "ENTITY_MOVE_LOOK": return a("ENTITY_RELATIVE_MOVE_AND_ROTATION");
      case "ENTITY_SOUND": return a("ENTITY_SOUND_EFFECT");
      case "EXPERIENCE": return a("SET_EXPERIENCE");
      case "GAME_STATE_CHANGE": return a("CHANGE_GAME_STATE");
      case "HELD_ITEM_SLOT": return a("HELD_ITEM_CHANGE");
      case "INITIALIZE_BORDER": return a("INITIALIZE_WORLD_BORDER");
      case "KICK_DISCONNECT": return a("DISCONNECT");
      case "LIGHT_UPDATE": return a("UPDATE_LIGHT");
      case "LOGIN": return a("JOIN_GAME");
      case "LOOK_AT": return a("FACE_PLAYER");
      case "MAP": return a("MAP_DATA");
      case "MAP_CHUNK": return a("CHUNK_DATA");
      case "MOUNT": return a("SET_PASSENGERS");
      case "NAMED_ENTITY_SPAWN": return a("SPAWN_PLAYER");
      case "NBT_QUERY": return a("NBT_QUERY_RESPONSE");
      case "OPEN_SIGN_ENTITY": return a("OPEN_SIGN_EDITOR");
      case "OPEN_WINDOW_HORSE": return a("OPEN_HORSE_WINDOW");
      case "OPEN_WINDOW_MERCHANT": return a("MERCHANT_OFFERS");
      case "PLAYER_INFO": return a("PLAYER_INFO", "PLAYER_INFO_UPDATE");
      case "PLAYER_LIST_HEADER_FOOTER": return a("PLAYER_LIST_HEADER_AND_FOOTER");
      case "POSITION": return a("PLAYER_POSITION_AND_LOOK");
      case "RECIPES": return a("DECLARE_RECIPES");
      case "RECIPE_UPDATE": return a("UNLOCK_RECIPES", "RECIPE_BOOK_ADD", "RECIPE_BOOK_REMOVE");
      case "REL_ENTITY_MOVE": return a("ENTITY_RELATIVE_MOVE");
      case "REL_ENTITY_MOVE_LOOK": return a("ENTITY_RELATIVE_MOVE_AND_ROTATION");
      case "SCOREBOARD_DISPLAY_OBJECTIVE": return a("DISPLAY_SCOREBOARD");
      case "SCOREBOARD_SCORE": return a("UPDATE_SCORE", "RESET_SCORE");
      case "SCOREBOARD_TEAM": return a("TEAMS");
      case "SELECT_ADVANCEMENT_TAB": return a("SELECT_ADVANCEMENTS_TAB");
      case "SET_BORDER_CENTER": return a("WORLD_BORDER_CENTER");
      case "SET_BORDER_LERP_SIZE": return a("WORLD_BORDER_LERP_SIZE");
      case "SET_BORDER_SIZE": return a("WORLD_BORDER_SIZE");
      case "SET_BORDER_WARNING_DELAY": return a("WORLD_BORDER_WARNING_DELAY");
      case "SET_BORDER_WARNING_DISTANCE": return a("WORLD_BORDER_WARNING_REACH");
      case "SPAWN_ENTITY_EXPERIENCE_ORB": return a("SPAWN_EXPERIENCE_ORB");
      case "SPAWN_ENTITY_LIVING": return a("SPAWN_LIVING_ENTITY");
      case "SPAWN_ENTITY_PAINTING": return a("SPAWN_PAINTING");
      case "SPAWN_ENTITY_WEATHER": return a("SPAWN_WEATHER_ENTITY");
      case "TILE_ENTITY_DATA": return a("BLOCK_ENTITY_DATA");
      case "TITLE": return a("TITLE", "SET_TITLE_TEXT", "SET_TITLE_SUBTITLE", "SET_TITLE_TIMES");
      case "TRANSACTION": return a("WINDOW_CONFIRMATION");
      case "UPDATE_TIME": return a("TIME_UPDATE");
      case "VIEW_CENTRE": return a("UPDATE_VIEW_POSITION");
      case "VIEW_DISTANCE": return a("UPDATE_VIEW_DISTANCE");
      case "WINDOW_DATA": return a("WINDOW_PROPERTY");
      case "WORLD_EVENT": return a("EFFECT");
      case "WORLD_PARTICLES": return a("PARTICLE");
      default: return a(name);
    }
  }

  private static String[] a(String... values) { return values; }
}

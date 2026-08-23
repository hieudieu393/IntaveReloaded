package de.jpx3.intave.packet.reader;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import de.jpx3.intave.module.linker.packet.PacketId;
import de.jpx3.intave.packet.nativeapi.events.NativePacket;
import de.jpx3.intave.module.linker.packet.PacketTypeResolver;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.*;

public final class PacketReaders {
  private static final Map<PacketTypeCommon, ThreadLocal<? extends PacketReader>> READER_LOCALS = new ConcurrentHashMap<>();

  private PacketReaders() {}

  public static void setup() {
    READER_LOCALS.clear();

    setup(ABILITIES_OUT, AbilityOutReader::new);
    setup(ANIMATION, AnimationReader::new);
    setup(ATTACH_ENTITY, AttachEntityReader::new);
    setup(BLOCK_ACTION, BlockActionReader::new);
    setup(BLOCK_CHANGE, SingleBlockChangeReader::new);
    setup(BLOCK_BREAK, SingleBlockChangeReader::new);
    setup(BLOCK_BREAK_ANIMATION, EntityReader::new);
    setup(CAMERA, EntityReader::new);
    setup(COLLECT, EntityReader::new);
    setup(COMBAT_EVENT, CombatEventReader::new);
    setup(ENTITY, EntityReader::new);
    setup(ENTITY_DESTROY, EntityDestroyReader::new);
    setup(ENTITY_EFFECT, EntityEffectReader::new);
    setup(ENTITY_EQUIPMENT, EntityReader::new);
    setup(ENTITY_HEAD_ROTATION, EntityReader::new);
    setup(ENTITY_LOOK, EntityReader::new);
    setup(ENTITY_METADATA, EntityMetadataReader::new);
    setup(ENTITY_MOVE_LOOK, EntityReader::new);
    setup(ENTITY_STATUS, EntityStatusReader::new);
    setup(ENTITY_SOUND, EntityReader::new);
    setup(ENTITY_TELEPORT, EntityReader::new);
    setup(ENTITY_VELOCITY, EntityVelocityReader::new);
    setup(EXPLOSION, ExplosionReader::new);
    setup(GAME_STATE_CHANGE, GameStateChangeReader::new);
    setup(LOGIN, EntityReader::new);
    setup(LOOK_AT, EntityReader::new);
    setup(MAP_CHUNK, MapChunkReader::new);
    setup(MAP_CHUNK_BULK, MapChunkBulkReader::new);
    setup(MOUNT, MountEntityReader::new);
    setup(MULTI_BLOCK_CHANGE, MultiBlockChangeReader::new);
    setup(NAMED_ENTITY_SPAWN, EntityReader::new);
    setup(OPEN_WINDOW, WindowOpenReader::new);
    setup(OPEN_WINDOW_HORSE, WindowOpenReader::new);
    setup(OPEN_SIGN_EDITOR, BlockPositionReader::new);
    setup(PacketId.Server.POSITION, PlayerTeleportReader::new);
    setup(PLAYER_INFO, PlayerInfoReader::new);
    setup(PLAYER_INFO_REMOVE, PlayerInfoRemoveReader::new);
    setup(REMOVE_ENTITY_EFFECT, EntityReader::new);
    setup(REL_ENTITY_MOVE, EntityReader::new);
    setup(REL_ENTITY_MOVE_LOOK, EntityReader::new);
    setup(SPAWN_ENTITY, EntityReader::new);
    setup(SPAWN_ENTITY_LIVING, EntityReader::new);
    setup(SPAWN_ENTITY_PAINTING, EntityReader::new);
    setup(SPAWN_ENTITY_WEATHER, EntityReader::new);
    setup(SPAWN_ENTITY_EXPERIENCE_ORB, EntityReader::new);
    setup(UPDATE_ATTRIBUTES, EntityReader::new);
    setup(UPDATE_ENTITY_NBT, EntityReader::new);
    setup(USE_BED, BedUseReader::new);
    setup(WINDOW_ITEMS, WindowBulkItemReader::new);
    setup(SET_SLOT, WindowSingleItemReader::new);
    setup(WORLD_BORDER, WorldBorderReader::new);
    setup(SET_BORDER_CENTER, WorldBorderReader::new);
    setup(SET_BORDER_SIZE, WorldBorderReader::new);
    setup(SET_BORDER_LERP_SIZE, WorldBorderReader::new);
    setup(SET_BORDER_WARNING_DELAY, WorldBorderReader::new);
    setup(SET_BORDER_WARNING_DISTANCE, WorldBorderReader::new);
    setup(INITIALIZE_BORDER, WorldBorderReader::new);
    setup(PacketId.Server.TRANSACTION, TransactionReader::new);

    setup(ABILITIES_IN, AbilityInReader::new);
    setup(ATTACK_ENTITY, EntityUseReader::new);
    setup(BLOCK_DIG, BlockDigReader::new);
    setup(BLOCK_PLACE, BlockInteractionReader::new);
    setup(PacketId.Client.CLOSE_WINDOW, WindowIdReader::new);
    setup(CUSTOM_PAYLOAD_IN, PayloadInReader::new);
    setup(ENCHANT_ITEM, WindowIdReader::new);
    setup(ENTITY_ACTION_IN, PlayerActionReader::new);
    setup(FLYING, PlayerMoveReader::new);
    setup(LOOK, PlayerMoveReader::new);
    setup(PacketId.Client.POSITION, PlayerMoveReader::new);
    setup(POSITION_LOOK, PlayerMoveReader::new);
    setup(STEER_VEHICLE, SteerVehicleReader::new);
    setup(PacketId.Client.TRANSACTION, TransactionReader::new);
    setup(PacketId.Client.UPDATE_SIGN, BlockPositionReader::new);
    setup(USE_ENTITY, EntityUseReader::new);
    setup(USE_ITEM, BlockInteractionReader::new);
    setup(USE_ITEM_ON, BlockInteractionReader::new);
    setup(PacketId.Client.VEHICLE_MOVE, PlayerMoveReader::new);
    setup(WINDOW_CLICK, WindowClickReader::new);
  }

  private static void setup(PacketId.Server id, Supplier<? extends PacketReader> supplier) {
    for (PacketTypeCommon type : PacketTypeResolver.server(new PacketId.Server[]{id})) {
      READER_LOCALS.put(type, ThreadLocal.withInitial(supplier));
    }
  }

  private static void setup(PacketId.Client id, Supplier<? extends PacketReader> supplier) {
    for (PacketTypeCommon type : PacketTypeResolver.client(new PacketId.Client[]{id})) {
      READER_LOCALS.put(type, ThreadLocal.withInitial(supplier));
    }
  }

  @SuppressWarnings("unchecked")
  public static <T extends PacketReader> T readerOf(ProtocolPacketEvent event) {
    ThreadLocal<? extends PacketReader> local = READER_LOCALS.get(event.getPacketType());
    if (local == null) {
      throw new IllegalStateException("No native PacketEvents reader for " + String.valueOf(event.getPacketType()));
    }
    PacketReader reader = local.get();
    reader.enter(event);
    return (T) reader;
  }

  public static <T extends PacketReader> T readerOf(NativePacket packet) {
    ProtocolPacketEvent event = packet.protocolEvent();
    if (event == null) throw new IllegalArgumentException("Native packet is not backed by a packet event");
    return readerOf(event);
  }

  public static boolean hasReader(PacketTypeCommon type) {
    return READER_LOCALS.containsKey(type);
  }
}

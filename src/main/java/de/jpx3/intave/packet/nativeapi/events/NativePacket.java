package de.jpx3.intave.packet.nativeapi.events;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import de.jpx3.intave.packet.nativeapi.PacketType;
import de.jpx3.intave.packet.nativeapi.reflect.EquivalentConverter;
import de.jpx3.intave.packet.nativeapi.reflect.NativeModifier;
import de.jpx3.intave.packet.nativeapi.wrappers.*;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/** PacketEvents-backed packet value container used by Intave's existing readers. */
public class NativePacket {
  private static final Object UNSUPPORTED = new Object();

  private final PacketType type;
  private PacketWrapper<?> wrapper;
  private final ProtocolPacketEvent event;

  public NativePacket(PacketType type) {
    this.type = type;
    this.event = null;
    this.wrapper = constructOutboundWrapper(type);
  }

  public NativePacket(PacketTypeCommon type) {
    this(PacketType.fromHandle(type));
  }

  private NativePacket(PacketType type, PacketWrapper<?> wrapper, ProtocolPacketEvent event) {
    this.type = type;
    this.wrapper = wrapper;
    this.event = event;
  }

  public static NativePacket fromEvent(ProtocolPacketEvent event) {
    PacketType type = PacketType.fromHandle(event.getPacketType());
    PacketWrapper<?> wrapper = event.getLastUsedWrapper();
    if (wrapper == null) wrapper = constructWrapper(event);
    if (wrapper != null) event.setLastUsedWrapper(wrapper);
    return new NativePacket(type, wrapper, event);
  }

  public static NativePacket fromPacket(Object packet) {
    if (packet instanceof NativePacket) return (NativePacket) packet;
    if (packet instanceof PacketWrapper) {
      PacketWrapper<?> wrapper = (PacketWrapper<?>) packet;
      return new NativePacket(PacketType.fromHandle(wrapper.getPacketTypeData().getPacketType()), wrapper, null);
    }
    return null;
  }

  private static PacketWrapper<?> constructWrapper(ProtocolPacketEvent event) {
    try {
      Class<? extends PacketWrapper<?>> clazz = event.getPacketType().getWrapperClass();
      if (clazz == null) return null;
      for (Constructor<?> constructor : clazz.getConstructors()) {
        Class<?>[] parameters = constructor.getParameterTypes();
        if (parameters.length == 1 && parameters[0].isAssignableFrom(event.getClass())) {
          return (PacketWrapper<?>) constructor.newInstance(event);
        }
        if (parameters.length == 1 && event instanceof PacketReceiveEvent && parameters[0] == PacketReceiveEvent.class) {
          return (PacketWrapper<?>) constructor.newInstance(event);
        }
        if (parameters.length == 1 && event instanceof PacketSendEvent && parameters[0] == PacketSendEvent.class) {
          return (PacketWrapper<?>) constructor.newInstance(event);
        }
      }
    } catch (Throwable ignored) {
    }
    return event.getLastUsedWrapper();
  }

  /**
   * ProtocolLib allowed creating an empty packet and filling its fields afterwards. PacketEvents uses
   * packet-specific wrappers, so create the real wrapper class with neutral constructor values first.
   */
  private static PacketWrapper<?> constructOutboundWrapper(PacketType type) {
    if (type == null || type.handle() == null) return null;
    PacketTypeCommon handle = type.handle();
    Class<? extends PacketWrapper<?>> wrapperClass = handle.getWrapperClass();
    if (wrapperClass == null || wrapperClass.getName().equals(PacketWrapper.class.getName())) {
      return new PacketWrapper(handle);
    }

    List<Constructor<?>> constructors = new ArrayList<Constructor<?>>(Arrays.asList(wrapperClass.getConstructors()));
    Collections.sort(constructors, new Comparator<Constructor<?>>() {
      @Override
      public int compare(Constructor<?> a, Constructor<?> b) {
        return Integer.compare(a.getParameterTypes().length, b.getParameterTypes().length);
      }
    });

    for (Constructor<?> constructor : constructors) {
      Class<?>[] parameterTypes = constructor.getParameterTypes();
      if (parameterTypes.length > 0 && ProtocolPacketEvent.class.isAssignableFrom(parameterTypes[0])) continue;
      Object[] arguments = new Object[parameterTypes.length];
      boolean supported = true;
      for (int i = 0; i < parameterTypes.length; i++) {
        arguments[i] = defaultValue(parameterTypes[i], 0);
        if (arguments[i] == UNSUPPORTED) {
          supported = false;
          break;
        }
      }
      if (!supported) continue;
      try {
        constructor.setAccessible(true);
        return (PacketWrapper<?>) constructor.newInstance(arguments);
      } catch (Throwable ignored) {
      }
    }

    // A base wrapper is still useful for packets without mutable packet-specific fields. If callers
    // try to write a packet-specific field, NativeModifier will fail loudly instead of sending corrupt data.
    return new PacketWrapper(handle);
  }

  private static Object defaultValue(Class<?> type, int depth) {
    if (type == boolean.class || type == Boolean.class) return false;
    if (type == byte.class || type == Byte.class) return (byte) 0;
    if (type == short.class || type == Short.class) return (short) 0;
    if (type == int.class || type == Integer.class) return 0;
    if (type == long.class || type == Long.class) return 0L;
    if (type == float.class || type == Float.class) return 0F;
    if (type == double.class || type == Double.class) return 0D;
    if (type == char.class || type == Character.class) return '\0';
    if (type == String.class) return "";
    if (type == UUID.class) return new UUID(0L, 0L);
    if (type == Optional.class) return Optional.empty();
    if (type == List.class || type == Collection.class || type == Iterable.class) return Collections.emptyList();
    if (type == Set.class) return Collections.emptySet();
    if (type == Map.class) return Collections.emptyMap();
    if (type.isArray()) return Array.newInstance(type.getComponentType(), 0);
    if (type.isEnum()) {
      Object[] constants = type.getEnumConstants();
      return constants != null && constants.length > 0 ? constants[0] : UNSUPPORTED;
    }

    String name = type.getName();
    try {
      if (name.equals("com.github.retrooper.packetevents.resources.ResourceLocation")) {
        return new ResourceLocation("minecraft:empty");
      }
      if (name.equals("com.github.retrooper.packetevents.util.Vector3i")) {
        return type.getConstructor(int.class, int.class, int.class).newInstance(0, 0, 0);
      }
      if (name.equals("com.github.retrooper.packetevents.util.Vector3d")) {
        return type.getConstructor(double.class, double.class, double.class).newInstance(0D, 0D, 0D);
      }
      if (name.equals("com.github.retrooper.packetevents.util.Vector3f")) {
        return type.getConstructor(float.class, float.class, float.class).newInstance(0F, 0F, 0F);
      }
      if (name.equals("com.github.retrooper.packetevents.protocol.entity.type.EntityType")) {
        Class<?> entityTypes = Class.forName("com.github.retrooper.packetevents.protocol.entity.type.EntityTypes");
        return entityTypes.getField("PLAYER").get(null);
      }

      for (String candidate : new String[]{"EMPTY", "AIR", "DEFAULT", "ZERO"}) {
        try {
          Field field = type.getField(candidate);
          if (Modifier.isStatic(field.getModifiers()) && type.isAssignableFrom(field.getType())) {
            Object value = field.get(null);
            if (value != null) return value;
          }
        } catch (Throwable ignored) {
        }
      }

      try {
        Constructor<?> noArgs = type.getDeclaredConstructor();
        noArgs.setAccessible(true);
        return noArgs.newInstance();
      } catch (Throwable ignored) {
      }

      if (depth < 2) {
        Constructor<?>[] nested = type.getConstructors();
        Arrays.sort(nested, new Comparator<Constructor<?>>() {
          @Override
          public int compare(Constructor<?> a, Constructor<?> b) {
            return Integer.compare(a.getParameterTypes().length, b.getParameterTypes().length);
          }
        });
        for (Constructor<?> constructor : nested) {
          Class<?>[] parameterTypes = constructor.getParameterTypes();
          Object[] args = new Object[parameterTypes.length];
          boolean ok = true;
          for (int i = 0; i < parameterTypes.length; i++) {
            args[i] = defaultValue(parameterTypes[i], depth + 1);
            if (args[i] == UNSUPPORTED) {
              ok = false;
              break;
            }
          }
          if (!ok) continue;
          try {
            return constructor.newInstance(args);
          } catch (Throwable ignored) {
          }
        }
      }
    } catch (Throwable ignored) {
    }

    // Most PacketEvents wrapper constructors accept null for optional reference data and only store it.
    return type.isPrimitive() ? UNSUPPORTED : null;
  }

  public PacketType getType() { return type; }
  public Object getHandle() { return wrapper; }
  public PacketWrapper<?> wrapper() { return wrapper; }
  public ProtocolPacketEvent protocolEvent() { return event; }
  public NativePacket shallowClone() { return new NativePacket(type, wrapper, event); }
  public NativePacket deepClone() { return shallowClone(); }
  public NativeModifier<Object> getModifier() { return new NativeModifier<Object>(wrapper); }
  public NativeModifier<Integer> getIntegers() { return getModifier().withType(int.class); }
  public NativeModifier<Long> getLongs() { return getModifier().withType(long.class); }
  public NativeModifier<Short> getShorts() { return getModifier().withType(short.class); }
  public NativeModifier<Byte> getBytes() { return getModifier().withType(byte.class); }
  public NativeModifier<Boolean> getBooleans() { return getModifier().withType(boolean.class); }
  public NativeModifier<Float> getFloat() { return getModifier().withType(float.class); }
  public NativeModifier<Double> getDoubles() { return getModifier().withType(double.class); }
  public NativeModifier<String> getStrings() { return getModifier().withType(String.class); }
  public NativeModifier<String[]> getStringArrays() { return getModifier().withType(String[].class); }
  public NativeModifier<int[]> getIntegerArrays() { return getModifier().withType(int[].class); }
  public NativeModifier<byte[]> getByteArrays() { return getModifier().withType(byte[].class); }
  public NativeModifier<UUID> getUUIDs() { return getModifier().withType(UUID.class); }
  public NativeModifier<Vector> getVectors() { return getModifier().withType(Vector.class); }
  public NativeModifier<ItemStack> getItemModifier() { return getModifier().withType(ItemStack.class); }
  public NativeModifier<ItemStack[]> getItemArrayModifier() { return getModifier().withType(ItemStack[].class); }
  @SuppressWarnings("unchecked") public NativeModifier<List<ItemStack>> getItemListModifier() { return (NativeModifier) getModifier().withType(List.class); }
  public NativeModifier<BlockPosition> getBlockPositionModifier() { return getModifier().withType(BlockPosition.class); }
  public NativeModifier<WrappedBlockData> getBlockData() { return getModifier().withType(WrappedBlockData.class); }
  public NativeModifier<NativeBlockHit> getMovingBlockPositions() { return getModifier().withType(NativeBlockHit.class); }
  public NativeModifier<MinecraftKey> getMinecraftKeys() { return getModifier().withType(MinecraftKey.class); }
  public NativeModifier<org.bukkit.Material> getBlocks() { return getModifier().withType(org.bukkit.Material.class); }
  public NativeModifier<BlockPosition[]> getSectionPositionArrays() { return getModifier().withType(BlockPosition[].class); }
  public NativeModifier<BlockPosition> getSectionPositions() { return getModifier().withType(BlockPosition.class); }
  public NativeModifier<short[]> getShortArrays() { return getModifier().withType(short[].class); }
  public NativeModifier<WrappedBlockData[]> getBlockDataArrays() { return getModifier().withType(WrappedBlockData[].class); }
  @SuppressWarnings("unchecked") public NativeModifier<Set<EnumWrappers.PlayerInfoAction>> getPlayerInfoActions() { return (NativeModifier) getModifier().withType(Set.class); }

  @SuppressWarnings("unchecked")
  public <T> NativeModifier<List<T>> getLists(final EquivalentConverter<T> converter) {
    return (NativeModifier) getModifier().withType(List.class, new EquivalentConverter<List<T>>() {
      @Override public Object getGeneric(List<T> values) {
        if (values == null) return null;
        List<Object> output = new ArrayList<Object>(values.size());
        for (T value : values) output.add(converter.getGeneric(value));
        return output;
      }
      @Override public List<T> getSpecific(Object value) {
        if (value == null) return null;
        List<T> output = new ArrayList<T>();
        for (Object element : (Iterable<?>) value) output.add(converter.getSpecific(element));
        return output;
      }
      @SuppressWarnings("unchecked") @Override public Class<List<T>> getSpecificType() { return (Class) List.class; }
    });
  }

  public NativeModifier<org.bukkit.entity.Entity> getEntityModifier(org.bukkit.World world) { return getModifier().withType(org.bukkit.entity.Entity.class); }
  @SuppressWarnings("unchecked") public NativeModifier<List<Integer>> getIntLists() { return (NativeModifier) getModifier().withType(List.class); }
  public NativeModifier<EnumWrappers.ClientCommand> getClientCommands() { return getModifier().withType(EnumWrappers.ClientCommand.class); }
  public NativeModifier<EnumWrappers.EntityUseAction> getEntityUseActions() { return getModifier().withType(EnumWrappers.EntityUseAction.class); }
  public NativeModifier<EnumWrappers.EnumEntityUseAction> getEnumEntityUseActions() { return getModifier().withType(EnumWrappers.EnumEntityUseAction.class); }
  public NativeModifier<org.bukkit.WorldType> getWorldTypeModifier() { return getModifier().withType(org.bukkit.WorldType.class); }
  public NativeModifier<EnumWrappers.PlayerDigType> getPlayerDigTypes() { return getModifier().withType(EnumWrappers.PlayerDigType.class); }
  public NativeModifier<EnumWrappers.Direction> getDirections() { return getModifier().withType(EnumWrappers.Direction.class); }
  public NativeModifier<EnumWrappers.Hand> getHands() { return getModifier().withType(EnumWrappers.Hand.class); }
  public NativeModifier<EnumWrappers.ChatType> getChatTypes() { return getModifier().withType(EnumWrappers.ChatType.class); }
  public NativeModifier<EnumWrappers.ItemSlot> getItemSlots() { return getModifier().withType(EnumWrappers.ItemSlot.class); }
  public NativeModifier<EnumWrappers.SoundCategory> getSoundCategories() { return getModifier().withType(EnumWrappers.SoundCategory.class); }
  public NativeModifier<EnumWrappers.PlayerInfoAction> getPlayerInfoAction() { return getModifier().withType(EnumWrappers.PlayerInfoAction.class); }
  @SuppressWarnings("unchecked") public NativeModifier<List<PlayerInfoData>> getPlayerInfoDataLists() { return (NativeModifier) getModifier().withType(List.class); }
  @SuppressWarnings("unchecked") public NativeModifier<List<Pair<EnumWrappers.ItemSlot, ItemStack>>> getSlotStackPairLists() { return (NativeModifier) getModifier().withType(List.class); }
  @SuppressWarnings("unchecked") public NativeModifier<List<WrappedAttribute>> getAttributeCollectionModifier() { return (NativeModifier) getModifier().withType(List.class); }
  @SuppressWarnings("unchecked") public NativeModifier<List<WrappedWatchableObject>> getWatchableCollectionModifier() { return (NativeModifier) getModifier().withType(List.class); }
  @SuppressWarnings("unchecked") public NativeModifier<List<WrappedDataValue>> getDataValueCollectionModifier() { return (NativeModifier) getModifier().withType(List.class); }
  public NativeModifier<WrappedDataWatcher> getDataWatcherModifier() { return getModifier().withType(WrappedDataWatcher.class); }
  public NativeModifier<WrappedChatComponent> getChatComponents() { return getModifier().withType(WrappedChatComponent.class); }
  public NativeModifier<WrappedChatComponent[]> getChatComponentArrays() { return getModifier().withType(WrappedChatComponent[].class); }
  public NativeModifier<PotionEffectType> getEffectTypes() { return getModifier().withType(PotionEffectType.class); }
  public NativeModifier<EntityType> getEntityTypeModifier() { return getModifier().withType(EntityType.class); }
  public NativeModifier<Sound> getSoundEffects() { return getModifier().withType(Sound.class); }
  public NativeModifier<WrappedParticle<?>> getNewParticles() { return (NativeModifier) getModifier().withType(WrappedParticle.class); }
  public NativeModifier<MultiBlockChangeInfo[]> getMultiBlockChangeInfoArrays() { return getModifier().withType(MultiBlockChangeInfo[].class); }
  @SuppressWarnings("unchecked") public NativeModifier<Iterable<NativePacket>> getPacketBundles() { return (NativeModifier) getModifier().withType(Iterable.class); }
  public NativeModifier<InternalStructure> getStructures() { return getModifier().withType(InternalStructure.class); }
  @SuppressWarnings("unchecked") public NativeModifier<Optional<InternalStructure>> getOptionalStructures() { return (NativeModifier) getModifier().withType(Optional.class); }
  public NativeModifier<CustomPacketPayloadWrapper> getCustomPacketPayloads() { return getModifier().withType(CustomPacketPayloadWrapper.class); }
  public <T> NativeModifier<T> getSpecificModifier(Class<T> clazz) { return getModifier().withType(clazz); }
  public <T extends Enum<T>> NativeModifier<T> getEnumModifier(Class<T> clazz, Object ignored) { return getModifier().withType(clazz); }

  @SuppressWarnings("unchecked")
  public <T> NativeModifier<Optional<T>> getOptionals(final EquivalentConverter<T> converter) {
    return (NativeModifier) getModifier().withType(Optional.class, new EquivalentConverter<Optional<T>>() {
      @Override public Object getGeneric(Optional<T> optional) {
        if (optional == null || !optional.isPresent()) return Optional.empty();
        return Optional.ofNullable(converter.getGeneric(optional.get()));
      }
      @Override public Optional<T> getSpecific(Object value) {
        if (!(value instanceof Optional) || !((Optional<?>) value).isPresent()) return Optional.empty();
        return Optional.ofNullable(converter.getSpecific(((Optional<?>) value).get()));
      }
      @SuppressWarnings("unchecked") @Override public Class<Optional<T>> getSpecificType() { return (Class) Optional.class; }
    });
  }

  @SuppressWarnings("unchecked")
  public <T> NativeModifier<Set<T>> getSets(final EquivalentConverter<T> converter) {
    return (NativeModifier) getModifier().withType(Set.class, new EquivalentConverter<Set<T>>() {
      @Override public Object getGeneric(Set<T> values) {
        if (values == null) return null;
        Set<Object> output = new LinkedHashSet<Object>();
        for (T value : values) output.add(converter.getGeneric(value));
        return output;
      }
      @Override public Set<T> getSpecific(Object value) {
        if (value == null) return null;
        Set<T> output = new LinkedHashSet<T>();
        for (Object element : (Iterable<?>) value) output.add(converter.getSpecific(element));
        return output;
      }
      @SuppressWarnings("unchecked") @Override public Class<Set<T>> getSpecificType() { return (Class) Set.class; }
    });
  }
}
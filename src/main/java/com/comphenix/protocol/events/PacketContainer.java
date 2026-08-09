package com.comphenix.protocol.events;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.reflect.EquivalentConverter;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.*;
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
public class PacketContainer {
  private static final Object UNSUPPORTED = new Object();

  private final PacketType type;
  private PacketWrapper<?> wrapper;
  private final ProtocolPacketEvent event;

  public PacketContainer(PacketType type) {
    this.type = type;
    this.event = null;
    this.wrapper = constructOutboundWrapper(type);
  }

  private PacketContainer(PacketType type, PacketWrapper<?> wrapper, ProtocolPacketEvent event) {
    this.type = type;
    this.wrapper = wrapper;
    this.event = event;
  }

  public static PacketContainer fromEvent(ProtocolPacketEvent event) {
    PacketType type = PacketType.fromHandle(event.getPacketType());
    PacketWrapper<?> wrapper = event.getLastUsedWrapper();
    if (wrapper == null) wrapper = constructWrapper(event);
    if (wrapper != null) event.setLastUsedWrapper(wrapper);
    return new PacketContainer(type, wrapper, event);
  }

  public static PacketContainer fromPacket(Object packet) {
    if (packet instanceof PacketContainer) return (PacketContainer) packet;
    if (packet instanceof PacketWrapper) {
      PacketWrapper<?> wrapper = (PacketWrapper<?>) packet;
      return new PacketContainer(PacketType.fromHandle(wrapper.getPacketTypeData().getPacketType()), wrapper, null);
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
    if (wrapperClass == null || wrapperClass == PacketWrapper.class) {
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
    // try to write a packet-specific field, StructureModifier will fail loudly instead of sending corrupt data.
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
  public PacketContainer shallowClone() { return new PacketContainer(type, wrapper, event); }
  public PacketContainer deepClone() { return shallowClone(); }
  public StructureModifier<Object> getModifier() { return new StructureModifier<Object>(wrapper); }
  public StructureModifier<Integer> getIntegers() { return getModifier().withType(int.class); }
  public StructureModifier<Long> getLongs() { return getModifier().withType(long.class); }
  public StructureModifier<Short> getShorts() { return getModifier().withType(short.class); }
  public StructureModifier<Byte> getBytes() { return getModifier().withType(byte.class); }
  public StructureModifier<Boolean> getBooleans() { return getModifier().withType(boolean.class); }
  public StructureModifier<Float> getFloat() { return getModifier().withType(float.class); }
  public StructureModifier<Double> getDoubles() { return getModifier().withType(double.class); }
  public StructureModifier<String> getStrings() { return getModifier().withType(String.class); }
  public StructureModifier<String[]> getStringArrays() { return getModifier().withType(String[].class); }
  public StructureModifier<int[]> getIntegerArrays() { return getModifier().withType(int[].class); }
  public StructureModifier<byte[]> getByteArrays() { return getModifier().withType(byte[].class); }
  public StructureModifier<UUID> getUUIDs() { return getModifier().withType(UUID.class); }
  public StructureModifier<Vector> getVectors() { return getModifier().withType(Vector.class); }
  public StructureModifier<ItemStack> getItemModifier() { return getModifier().withType(ItemStack.class); }
  public StructureModifier<ItemStack[]> getItemArrayModifier() { return getModifier().withType(ItemStack[].class); }
  @SuppressWarnings("unchecked") public StructureModifier<List<ItemStack>> getItemListModifier() { return (StructureModifier) getModifier().withType(List.class); }
  public StructureModifier<BlockPosition> getBlockPositionModifier() { return getModifier().withType(BlockPosition.class); }
  public StructureModifier<WrappedBlockData> getBlockData() { return getModifier().withType(WrappedBlockData.class); }
  public StructureModifier<MovingObjectPositionBlock> getMovingBlockPositions() { return getModifier().withType(MovingObjectPositionBlock.class); }
  public StructureModifier<MinecraftKey> getMinecraftKeys() { return getModifier().withType(MinecraftKey.class); }
  public StructureModifier<org.bukkit.Material> getBlocks() { return getModifier().withType(org.bukkit.Material.class); }
  public StructureModifier<BlockPosition[]> getSectionPositionArrays() { return getModifier().withType(BlockPosition[].class); }
  public StructureModifier<BlockPosition> getSectionPositions() { return getModifier().withType(BlockPosition.class); }
  public StructureModifier<short[]> getShortArrays() { return getModifier().withType(short[].class); }
  public StructureModifier<WrappedBlockData[]> getBlockDataArrays() { return getModifier().withType(WrappedBlockData[].class); }
  @SuppressWarnings("unchecked") public StructureModifier<Set<EnumWrappers.PlayerInfoAction>> getPlayerInfoActions() { return (StructureModifier) getModifier().withType(Set.class); }

  @SuppressWarnings("unchecked")
  public <T> StructureModifier<List<T>> getLists(final EquivalentConverter<T> converter) {
    return (StructureModifier) getModifier().withType(List.class, new EquivalentConverter<List<T>>() {
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

  public StructureModifier<org.bukkit.entity.Entity> getEntityModifier(org.bukkit.World world) { return getModifier().withType(org.bukkit.entity.Entity.class); }
  @SuppressWarnings("unchecked") public StructureModifier<List<Integer>> getIntLists() { return (StructureModifier) getModifier().withType(List.class); }
  public StructureModifier<EnumWrappers.ClientCommand> getClientCommands() { return getModifier().withType(EnumWrappers.ClientCommand.class); }
  public StructureModifier<EnumWrappers.EntityUseAction> getEntityUseActions() { return getModifier().withType(EnumWrappers.EntityUseAction.class); }
  public StructureModifier<EnumWrappers.EnumEntityUseAction> getEnumEntityUseActions() { return getModifier().withType(EnumWrappers.EnumEntityUseAction.class); }
  public StructureModifier<org.bukkit.WorldType> getWorldTypeModifier() { return getModifier().withType(org.bukkit.WorldType.class); }
  public StructureModifier<EnumWrappers.PlayerDigType> getPlayerDigTypes() { return getModifier().withType(EnumWrappers.PlayerDigType.class); }
  public StructureModifier<EnumWrappers.Direction> getDirections() { return getModifier().withType(EnumWrappers.Direction.class); }
  public StructureModifier<EnumWrappers.Hand> getHands() { return getModifier().withType(EnumWrappers.Hand.class); }
  public StructureModifier<EnumWrappers.ChatType> getChatTypes() { return getModifier().withType(EnumWrappers.ChatType.class); }
  public StructureModifier<EnumWrappers.ItemSlot> getItemSlots() { return getModifier().withType(EnumWrappers.ItemSlot.class); }
  public StructureModifier<EnumWrappers.SoundCategory> getSoundCategories() { return getModifier().withType(EnumWrappers.SoundCategory.class); }
  public StructureModifier<EnumWrappers.PlayerInfoAction> getPlayerInfoAction() { return getModifier().withType(EnumWrappers.PlayerInfoAction.class); }
  @SuppressWarnings("unchecked") public StructureModifier<List<PlayerInfoData>> getPlayerInfoDataLists() { return (StructureModifier) getModifier().withType(List.class); }
  @SuppressWarnings("unchecked") public StructureModifier<List<Pair<EnumWrappers.ItemSlot, ItemStack>>> getSlotStackPairLists() { return (StructureModifier) getModifier().withType(List.class); }
  @SuppressWarnings("unchecked") public StructureModifier<List<WrappedAttribute>> getAttributeCollectionModifier() { return (StructureModifier) getModifier().withType(List.class); }
  @SuppressWarnings("unchecked") public StructureModifier<List<WrappedWatchableObject>> getWatchableCollectionModifier() { return (StructureModifier) getModifier().withType(List.class); }
  @SuppressWarnings("unchecked") public StructureModifier<List<WrappedDataValue>> getDataValueCollectionModifier() { return (StructureModifier) getModifier().withType(List.class); }
  public StructureModifier<WrappedDataWatcher> getDataWatcherModifier() { return getModifier().withType(WrappedDataWatcher.class); }
  public StructureModifier<WrappedChatComponent> getChatComponents() { return getModifier().withType(WrappedChatComponent.class); }
  public StructureModifier<WrappedChatComponent[]> getChatComponentArrays() { return getModifier().withType(WrappedChatComponent[].class); }
  public StructureModifier<PotionEffectType> getEffectTypes() { return getModifier().withType(PotionEffectType.class); }
  public StructureModifier<EntityType> getEntityTypeModifier() { return getModifier().withType(EntityType.class); }
  public StructureModifier<Sound> getSoundEffects() { return getModifier().withType(Sound.class); }
  public StructureModifier<WrappedParticle<?>> getNewParticles() { return (StructureModifier) getModifier().withType(WrappedParticle.class); }
  public StructureModifier<MultiBlockChangeInfo[]> getMultiBlockChangeInfoArrays() { return getModifier().withType(MultiBlockChangeInfo[].class); }
  @SuppressWarnings("unchecked") public StructureModifier<Iterable<PacketContainer>> getPacketBundles() { return (StructureModifier) getModifier().withType(Iterable.class); }
  public StructureModifier<InternalStructure> getStructures() { return getModifier().withType(InternalStructure.class); }
  @SuppressWarnings("unchecked") public StructureModifier<Optional<InternalStructure>> getOptionalStructures() { return (StructureModifier) getModifier().withType(Optional.class); }
  public StructureModifier<CustomPacketPayloadWrapper> getCustomPacketPayloads() { return getModifier().withType(CustomPacketPayloadWrapper.class); }
  public <T> StructureModifier<T> getSpecificModifier(Class<T> clazz) { return getModifier().withType(clazz); }
  public <T extends Enum<T>> StructureModifier<T> getEnumModifier(Class<T> clazz, Object ignored) { return getModifier().withType(clazz); }

  @SuppressWarnings("unchecked")
  public <T> StructureModifier<Optional<T>> getOptionals(final EquivalentConverter<T> converter) {
    return (StructureModifier) getModifier().withType(Optional.class, new EquivalentConverter<Optional<T>>() {
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
  public <T> StructureModifier<Set<T>> getSets(final EquivalentConverter<T> converter) {
    return (StructureModifier) getModifier().withType(Set.class, new EquivalentConverter<Set<T>>() {
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
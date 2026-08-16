package de.jpx3.intave.packet.nativeapi.reflect;

import de.jpx3.intave.packet.nativeapi.events.InternalStructure;
import de.jpx3.intave.packet.nativeapi.wrappers.BlockPosition;
import de.jpx3.intave.packet.nativeapi.wrappers.CustomPacketPayloadWrapper;
import de.jpx3.intave.packet.nativeapi.wrappers.EnumWrappers;
import de.jpx3.intave.packet.nativeapi.wrappers.MinecraftKey;
import de.jpx3.intave.packet.nativeapi.wrappers.WrappedBlockData;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.util.Vector3i;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Reflection field view used by the legacy Intave readers over PacketEvents wrappers.
 * The requested type is the old Intave/ProtocolLib-facing type while the actual field is a PacketEvents type.
 */
public class NativeModifier<T> {
  private final Class<?> targetClass;
  private Object target;
  private final Class<?> fieldType;
  private final EquivalentConverter<T> converter;

  public NativeModifier(Class<?> targetClass, Object ignored, boolean requireDefault) {
    this(targetClass, null, Object.class, null);
  }

  public NativeModifier(Object target) {
    this(target == null ? Object.class : target.getClass(), target, Object.class, null);
  }

  private NativeModifier(Class<?> targetClass, Object target, Class<?> fieldType, EquivalentConverter<T> converter) {
    this.targetClass = targetClass == null ? Object.class : targetClass;
    this.target = target;
    this.fieldType = fieldType;
    this.converter = converter;
  }

  public NativeModifier<T> withTarget(Object target) {
    return new NativeModifier<T>(targetClass, target, fieldType, converter);
  }

  public <R> NativeModifier<R> withType(Class<?> type) {
    return new NativeModifier<R>(targetClass, target, type, null);
  }

  public <R> NativeModifier<R> withType(Class<?> type, EquivalentConverter<R> converter) {
    return new NativeModifier<R>(targetClass, target, type, converter);
  }

  public <R> NativeModifier<R> withConverter(EquivalentConverter<R> converter) {
    return new NativeModifier<R>(targetClass, target, fieldType, converter);
  }

  private static Class<?> box(Class<?> type) {
    if (!type.isPrimitive()) return type;
    if (type == int.class) return Integer.class;
    if (type == long.class) return Long.class;
    if (type == double.class) return Double.class;
    if (type == float.class) return Float.class;
    if (type == short.class) return Short.class;
    if (type == byte.class) return Byte.class;
    if (type == boolean.class) return Boolean.class;
    if (type == char.class) return Character.class;
    return type;
  }

  private List<Field> allFields() {
    List<Field> output = new ArrayList<Field>();
    for (Class<?> type = targetClass; type != null && type != Object.class; type = type.getSuperclass()) {
      if (type.getName().equals("com.github.retrooper.packetevents.wrapper.PacketWrapper")) break;
      for (Field field : type.getDeclaredFields()) {
        if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
        try {
          field.setAccessible(true);
        } catch (Throwable ignored) {
        }
        output.add(field);
      }
    }
    return output;
  }

  private List<Field> fields() {
    List<Field> output = new ArrayList<Field>();
    for (Field field : allFields()) {
      if (fieldType == Object.class || compatible(fieldType, field.getType())) output.add(field);
    }
    return output;
  }

  private static boolean compatible(Class<?> requested, Class<?> nativeType) {
    Class<?> requestedBox = box(requested);
    Class<?> nativeBox = box(nativeType);
    if (requestedBox.isAssignableFrom(nativeBox) || nativeBox.isAssignableFrom(requestedBox)) return true;

    if (requested == BlockPosition.class && nativeType.getName().equals(Vector3i.class.getName())) return true;
    if (requested == Vector.class && isPacketEventsVector(nativeType)) return true;
    if (requested == MinecraftKey.class && (nativeType == String.class || nativeType.getName().equals(ResourceLocation.class.getName()))) return true;
    if (requested == ItemStack.class && nativeType.getName().equals("com.github.retrooper.packetevents.protocol.item.ItemStack")) return true;
    if (requested == EntityType.class && nativeType.getName().equals("com.github.retrooper.packetevents.protocol.entity.type.EntityType")) return true;
    if (requested == PotionEffectType.class && nativeType.getName().equals("com.github.retrooper.packetevents.protocol.potion.PotionType")) return true;
    if (requested == WrappedBlockData.class && nativeType.getName().equals("com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState")) return true;
    if (requested == InternalStructure.class && !nativeType.isPrimitive()) return true;
    if (requested == EnumWrappers.EnumEntityUseAction.class && nativeType.isEnum()) return true;
    if (requested.isEnum() && nativeType.isEnum()) return true;

    if (requested.isArray() && nativeType.isArray()) {
      return compatible(requested.getComponentType(), nativeType.getComponentType());
    }
    return false;
  }

  private static boolean isPacketEventsVector(Class<?> type) {
    String name = type.getName();
    return name.equals(Vector3i.class.getName()) || name.equals(Vector3d.class.getName()) || name.equals(Vector3f.class.getName());
  }

  private boolean isPluginMessageComposite() {
    return fieldType == CustomPacketPayloadWrapper.class && target != null && targetClass.getName().contains("PluginMessage");
  }

  @SuppressWarnings("unchecked")
  public T read(int index) {
    try {
      if (isPluginMessageComposite()) {
        if (index != 0) throw new IndexOutOfBoundsException("Plugin payload index " + index);
        Object channel = firstFieldValue(String.class, ResourceLocation.class);
        byte[] data = (byte[]) firstFieldValue(byte[].class);
        MinecraftKey key = channel instanceof ResourceLocation
          ? new MinecraftKey(channel.toString())
          : new MinecraftKey(channel == null ? "minecraft:empty" : String.valueOf(channel));
        return (T) new CustomPacketPayloadWrapper(data == null ? new byte[0] : data, key);
      }

      Field field = fields().get(index);
      Object value = field.get(target);
      if (converter != null) return converter.getSpecific(value);
      return (T) toRequested(value, fieldType);
    } catch (Exception exception) {
      throw new FieldAccessException("Unable to read field " + index + " from " + targetClass.getName(), exception);
    }
  }

  public T readSafely(int index) {
    try {
      return index >= 0 && index < size() ? read(index) : null;
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  public NativeModifier<T> write(int index, T value) {
    try {
      if (isPluginMessageComposite()) {
        if (index != 0) throw new IndexOutOfBoundsException("Plugin payload index " + index);
        CustomPacketPayloadWrapper payload = (CustomPacketPayloadWrapper) value;
        if (payload == null) return this;
        setFirstField(payload.getId(), String.class, ResourceLocation.class);
        setFirstField(payload.getPayload(), byte[].class);
        return this;
      }

      Field field = fields().get(index);
      Object generic = converter == null ? toNative(value, field.getType()) : converter.getGeneric(value);
      field.set(target, generic);
      return this;
    } catch (Exception exception) {
      throw new FieldAccessException("Unable to write field " + index + " on " + targetClass.getName(), exception);
    }
  }

  public NativeModifier<T> writeSafely(int index, T value) {
    if (index >= 0 && index < size()) write(index, value);
    return this;
  }

  public int size() {
    return isPluginMessageComposite() ? 1 : fields().size();
  }

  public List<T> getValues() {
    List<T> output = new ArrayList<T>();
    for (int i = 0; i < size(); i++) output.add(readSafely(i));
    return output;
  }

  public List<Field> getFields() {
    return Collections.unmodifiableList(fields());
  }

  public Field getField(int index) {
    return fields().get(index);
  }

  private Object firstFieldValue(Class<?>... acceptedTypes) throws IllegalAccessException {
    for (Field field : allFields()) {
      for (Class<?> acceptedType : acceptedTypes) {
        if (acceptedType.isAssignableFrom(field.getType())) return field.get(target);
      }
    }
    return null;
  }

  private void setFirstField(Object requestedValue, Class<?>... acceptedTypes) throws IllegalAccessException {
    for (Field field : allFields()) {
      for (Class<?> acceptedType : acceptedTypes) {
        if (acceptedType.isAssignableFrom(field.getType())) {
          field.set(target, toNative(requestedValue, field.getType()));
          return;
        }
      }
    }
  }

  private static Object toRequested(Object value, Class<?> requestedType) {
    if (value == null) return null;
    Class<?> boxedRequested = box(requestedType);
    if (boxedRequested.isInstance(value)) return value;

    if (requestedType == BlockPosition.class && value instanceof Vector3i) {
      Vector3i vector = (Vector3i) value;
      return new BlockPosition(vector.getX(), vector.getY(), vector.getZ());
    }
    if (requestedType == Vector.class && isPacketEventsVector(value.getClass())) {
      return new Vector(number(value, "getX"), number(value, "getY"), number(value, "getZ"));
    }
    if (requestedType == MinecraftKey.class && (value instanceof ResourceLocation || value instanceof String)) {
      return new MinecraftKey(String.valueOf(value));
    }
    if (requestedType == ItemStack.class && value.getClass().getName().equals("com.github.retrooper.packetevents.protocol.item.ItemStack")) {
      return SpigotConversionUtil.toBukkitItemStack((com.github.retrooper.packetevents.protocol.item.ItemStack) value);
    }
    if (requestedType == EntityType.class && value.getClass().getName().equals("com.github.retrooper.packetevents.protocol.entity.type.EntityType")) {
      return SpigotConversionUtil.toBukkitEntityType((com.github.retrooper.packetevents.protocol.entity.type.EntityType) value);
    }
    if (requestedType == PotionEffectType.class && value.getClass().getName().equals("com.github.retrooper.packetevents.protocol.potion.PotionType")) {
      return SpigotConversionUtil.toBukkitPotionEffectType((com.github.retrooper.packetevents.protocol.potion.PotionType) value);
    }
    if (requestedType == WrappedBlockData.class && value.getClass().getName().equals("com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState")) {
      return WrappedBlockData.fromHandle(value);
    }
    if (requestedType == InternalStructure.class) return new InternalStructure(value);
    if (requestedType == EnumWrappers.EnumEntityUseAction.class && value instanceof Enum) {
      EnumWrappers.EntityUseAction action = enumByName(EnumWrappers.EntityUseAction.class, ((Enum<?>) value).name());
      return action == null ? null : new EnumWrappers.EnumEntityUseAction(action);
    }
    if (requestedType.isEnum() && value instanceof Enum) {
      return enumByName((Class<? extends Enum>) requestedType, ((Enum<?>) value).name());
    }
    if (requestedType.isArray() && value.getClass().isArray()) {
      int length = Array.getLength(value);
      Object converted = Array.newInstance(requestedType.getComponentType(), length);
      for (int i = 0; i < length; i++) Array.set(converted, i, toRequested(Array.get(value, i), requestedType.getComponentType()));
      return converted;
    }
    return value;
  }

  private static Object toNative(Object value, Class<?> nativeType) {
    if (value == null) return null;
    Class<?> boxedNative = box(nativeType);
    if (boxedNative.isInstance(value)) return value;

    if (value instanceof BlockPosition && nativeType.getName().equals(Vector3i.class.getName())) {
      BlockPosition position = (BlockPosition) value;
      return new Vector3i(position.getX(), position.getY(), position.getZ());
    }
    if (value instanceof Vector && isPacketEventsVector(nativeType)) {
      Vector vector = (Vector) value;
      if (nativeType.getName().equals(Vector3i.class.getName())) return new Vector3i(vector.getBlockX(), vector.getBlockY(), vector.getBlockZ());
      if (nativeType.getName().equals(Vector3f.class.getName())) return new Vector3f((float) vector.getX(), (float) vector.getY(), (float) vector.getZ());
      return new Vector3d(vector.getX(), vector.getY(), vector.getZ());
    }
    if (value instanceof MinecraftKey) {
      MinecraftKey key = (MinecraftKey) value;
      if (nativeType == String.class) return key.getFullKey();
      if (nativeType.getName().equals(ResourceLocation.class.getName())) return new ResourceLocation(key.getFullKey());
    }
    if (value instanceof ItemStack && nativeType.getName().equals("com.github.retrooper.packetevents.protocol.item.ItemStack")) {
      return SpigotConversionUtil.fromBukkitItemStack((ItemStack) value);
    }
    if (value instanceof EntityType && nativeType.getName().equals("com.github.retrooper.packetevents.protocol.entity.type.EntityType")) {
      return SpigotConversionUtil.fromBukkitEntityType((EntityType) value);
    }
    if (value instanceof PotionEffectType && nativeType.getName().equals("com.github.retrooper.packetevents.protocol.potion.PotionType")) {
      return SpigotConversionUtil.fromBukkitPotionEffectType((PotionEffectType) value);
    }
    if (value instanceof WrappedBlockData && nativeType.getName().equals("com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState")) {
      Object converted = convertBlockData((WrappedBlockData) value, nativeType);
      if (converted != null) return converted;
    }
    if (value instanceof InternalStructure) return ((InternalStructure) value).getHandle();
    if (value instanceof EnumWrappers.EnumEntityUseAction && nativeType.isEnum()) {
      return enumByName((Class<? extends Enum>) nativeType, ((EnumWrappers.EnumEntityUseAction) value).getAction().name());
    }
    if (value instanceof Enum && nativeType.isEnum()) {
      return enumByName((Class<? extends Enum>) nativeType, ((Enum<?>) value).name());
    }
    if (value.getClass().isArray() && nativeType.isArray()) {
      int length = Array.getLength(value);
      Object converted = Array.newInstance(nativeType.getComponentType(), length);
      for (int i = 0; i < length; i++) Array.set(converted, i, toNative(Array.get(value, i), nativeType.getComponentType()));
      return converted;
    }
    return value;
  }

  private static Object convertBlockData(WrappedBlockData value, Class<?> nativeType) {
    Object handle = value.getHandle();
    if (handle != null && nativeType.isInstance(handle)) return handle;
    org.bukkit.Material material = value.getType();
    if (material == null && handle instanceof org.bukkit.Material) material = (org.bukkit.Material) handle;
    if (material == null) return null;

    try {
      // 1.13+: Material#createBlockData, invoked reflectively so old Spigot versions can still load this class.
      Method createBlockData = material.getClass().getMethod("createBlockData");
      Object blockData = createBlockData.invoke(material);
      for (Method method : SpigotConversionUtil.class.getMethods()) {
        if (method.getName().equals("fromBukkitBlockData") && method.getParameterTypes().length == 1 && method.getParameterTypes()[0].isInstance(blockData)) {
          return method.invoke(null, blockData);
        }
      }
    } catch (Throwable ignored) {
    }

    try {
      Object materialData = Class.forName("org.bukkit.material.MaterialData").getConstructor(org.bukkit.Material.class).newInstance(material);
      for (Method method : SpigotConversionUtil.class.getMethods()) {
        if (method.getName().equals("fromBukkitMaterialData") && method.getParameterTypes().length == 1 && method.getParameterTypes()[0].isInstance(materialData)) {
          return method.invoke(null, materialData);
        }
      }
    } catch (Throwable ignored) {
    }
    return null;
  }

  private static double number(Object value, String getter) {
    try {
      return ((Number) value.getClass().getMethod(getter).invoke(value)).doubleValue();
    } catch (Exception ignored) {
      return 0D;
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static <E extends Enum> E enumByName(Class<E> type, String name) {
    if (name == null) return null;
    String normalized = name.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    try {
      return (E) Enum.valueOf(type, normalized);
    } catch (IllegalArgumentException ignored) {
      // PacketEvents occasionally uses semantically identical aliases. Try the common Intave names.
      if (normalized.equals("MAIN_HAND")) normalized = "MAINHAND";
      else if (normalized.equals("OFF_HAND")) normalized = "OFFHAND";
      else if (normalized.equals("MAINHAND")) normalized = "MAIN_HAND";
      else if (normalized.equals("OFFHAND")) normalized = "OFF_HAND";
      try {
        return (E) Enum.valueOf(type, normalized);
      } catch (IllegalArgumentException ignoredAgain) {
        return null;
      }
    }
  }
}
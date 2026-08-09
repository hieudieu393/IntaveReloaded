package com.comphenix.protocol.events;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
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

import java.lang.reflect.Constructor;
import java.util.*;

/** PacketEvents-backed packet value container used by Intave's existing readers. */
public class PacketContainer {
  private final PacketType type;
  private PacketWrapper<?> wrapper;
  private final ProtocolPacketEvent event;

  public PacketContainer(PacketType type) {
    this.type=type; this.event=null;
    this.wrapper = type != null && type.handle() != null ? new PacketWrapper(type.handle()) : null;
  }
  private PacketContainer(PacketType type, PacketWrapper<?> wrapper, ProtocolPacketEvent event) { this.type=type;this.wrapper=wrapper;this.event=event; }

  public static PacketContainer fromEvent(ProtocolPacketEvent event) {
    PacketType type=PacketType.fromHandle(event.getPacketType());
    PacketWrapper<?> wrapper=event.getLastUsedWrapper();
    if(wrapper==null) wrapper=constructWrapper(event);
    if(wrapper!=null) event.setLastUsedWrapper(wrapper);
    return new PacketContainer(type,wrapper,event);
  }
  public static PacketContainer fromPacket(Object packet) {
    if(packet instanceof PacketContainer) return (PacketContainer)packet;
    if(packet instanceof PacketWrapper) {
      PacketWrapper<?> w=(PacketWrapper<?>)packet;
      return new PacketContainer(PacketType.fromHandle(w.getPacketTypeData().getPacketType()), w, null);
    }
    return null;
  }
  private static PacketWrapper<?> constructWrapper(ProtocolPacketEvent event) {
    try {
      Class<? extends PacketWrapper<?>> clazz=event.getPacketType().getWrapperClass();
      if(clazz==null) return null;
      for(Constructor<?> c:clazz.getConstructors()) {
        Class<?>[] p=c.getParameterTypes();
        if(p.length==1 && p[0].isAssignableFrom(event.getClass())) return (PacketWrapper<?>)c.newInstance(event);
        if(p.length==1 && event instanceof PacketReceiveEvent && p[0]==PacketReceiveEvent.class) return (PacketWrapper<?>)c.newInstance(event);
        if(p.length==1 && event instanceof PacketSendEvent && p[0]==PacketSendEvent.class) return (PacketWrapper<?>)c.newInstance(event);
      }
    } catch(Throwable ignored) {}
    return event.getLastUsedWrapper();
  }

  public PacketType getType(){return type;}
  public Object getHandle(){return wrapper;}
  public PacketWrapper<?> wrapper(){return wrapper;}
  public PacketContainer shallowClone(){return new PacketContainer(type,wrapper,event);}
  public PacketContainer deepClone(){return shallowClone();}
  public StructureModifier<Object> getModifier(){return new StructureModifier<>(wrapper);}
  public StructureModifier<Integer> getIntegers(){return getModifier().withType(int.class);}
  public StructureModifier<Long> getLongs(){return getModifier().withType(long.class);}
  public StructureModifier<Short> getShorts(){return getModifier().withType(short.class);}
  public StructureModifier<Byte> getBytes(){return getModifier().withType(byte.class);}
  public StructureModifier<Boolean> getBooleans(){return getModifier().withType(boolean.class);}
  public StructureModifier<Float> getFloat(){return getModifier().withType(float.class);}
  public StructureModifier<Double> getDoubles(){return getModifier().withType(double.class);}
  public StructureModifier<String> getStrings(){return getModifier().withType(String.class);}
  public StructureModifier<String[]> getStringArrays(){return getModifier().withType(String[].class);}
  public StructureModifier<int[]> getIntegerArrays(){return getModifier().withType(int[].class);}
  public StructureModifier<byte[]> getByteArrays(){return getModifier().withType(byte[].class);}
  public StructureModifier<UUID> getUUIDs(){return getModifier().withType(UUID.class);}
  public StructureModifier<Vector> getVectors(){return getModifier().withType(Vector.class);}
  public StructureModifier<ItemStack> getItemModifier(){return getModifier().withType(ItemStack.class);}
  public StructureModifier<ItemStack[]> getItemArrayModifier(){return getModifier().withType(ItemStack[].class);}
  @SuppressWarnings("unchecked") public StructureModifier<List<ItemStack>> getItemListModifier(){return (StructureModifier)getModifier().withType(List.class);}
  public StructureModifier<BlockPosition> getBlockPositionModifier(){return getModifier().withType(BlockPosition.class);}
  public StructureModifier<WrappedBlockData> getBlockData(){return getModifier().withType(WrappedBlockData.class);}
  public StructureModifier<MovingObjectPositionBlock> getMovingBlockPositions(){return getModifier().withType(MovingObjectPositionBlock.class);}
  public StructureModifier<MinecraftKey> getMinecraftKeys(){return getModifier().withType(MinecraftKey.class);}
  public StructureModifier<org.bukkit.Material> getBlocks(){return getModifier().withType(org.bukkit.Material.class);}
  public StructureModifier<BlockPosition[]> getSectionPositionArrays(){return getModifier().withType(BlockPosition[].class);}
  public StructureModifier<BlockPosition> getSectionPositions(){return getModifier().withType(BlockPosition.class);}
  public StructureModifier<short[]> getShortArrays(){return getModifier().withType(short[].class);}
  public StructureModifier<WrappedBlockData[]> getBlockDataArrays(){return getModifier().withType(WrappedBlockData[].class);}
  @SuppressWarnings("unchecked") public StructureModifier<Set<EnumWrappers.PlayerInfoAction>> getPlayerInfoActions(){return (StructureModifier)getModifier().withType(Set.class);}
  @SuppressWarnings("unchecked") public <T> StructureModifier<List<T>> getLists(EquivalentConverter<T> converter){return (StructureModifier)getModifier().withType(List.class);}
  public StructureModifier<org.bukkit.entity.Entity> getEntityModifier(org.bukkit.World world){return getModifier().withType(org.bukkit.entity.Entity.class);}

  @SuppressWarnings("unchecked") public StructureModifier<List<Integer>> getIntLists(){return (StructureModifier)getModifier().withType(List.class);}
  public StructureModifier<EnumWrappers.ClientCommand> getClientCommands(){return getModifier().withType(EnumWrappers.ClientCommand.class);}
  public StructureModifier<EnumWrappers.EntityUseAction> getEntityUseActions(){return getModifier().withType(EnumWrappers.EntityUseAction.class);}
  public StructureModifier<EnumWrappers.EnumEntityUseAction> getEnumEntityUseActions(){return getModifier().withType(EnumWrappers.EnumEntityUseAction.class);}
  public StructureModifier<org.bukkit.WorldType> getWorldTypeModifier(){return getModifier().withType(org.bukkit.WorldType.class);}
  public StructureModifier<EnumWrappers.PlayerDigType> getPlayerDigTypes(){return getModifier().withType(EnumWrappers.PlayerDigType.class);}
  public StructureModifier<EnumWrappers.Direction> getDirections(){return getModifier().withType(EnumWrappers.Direction.class);}
  public StructureModifier<EnumWrappers.Hand> getHands(){return getModifier().withType(EnumWrappers.Hand.class);}
  public StructureModifier<EnumWrappers.ChatType> getChatTypes(){return getModifier().withType(EnumWrappers.ChatType.class);}
  public StructureModifier<EnumWrappers.ItemSlot> getItemSlots(){return getModifier().withType(EnumWrappers.ItemSlot.class);}
  public StructureModifier<EnumWrappers.SoundCategory> getSoundCategories(){return getModifier().withType(EnumWrappers.SoundCategory.class);}
  public StructureModifier<EnumWrappers.PlayerInfoAction> getPlayerInfoAction(){return getModifier().withType(EnumWrappers.PlayerInfoAction.class);}
  @SuppressWarnings("unchecked") public StructureModifier<List<PlayerInfoData>> getPlayerInfoDataLists(){return (StructureModifier)getModifier().withType(List.class);}
  @SuppressWarnings("unchecked") public StructureModifier<List<Pair<EnumWrappers.ItemSlot,ItemStack>>> getSlotStackPairLists(){return (StructureModifier)getModifier().withType(List.class);}
  @SuppressWarnings("unchecked") public StructureModifier<List<WrappedAttribute>> getAttributeCollectionModifier(){return (StructureModifier)getModifier().withType(List.class);}
  @SuppressWarnings("unchecked") public StructureModifier<List<WrappedWatchableObject>> getWatchableCollectionModifier(){return (StructureModifier)getModifier().withType(List.class);}
  @SuppressWarnings("unchecked") public StructureModifier<List<WrappedDataValue>> getDataValueCollectionModifier(){return (StructureModifier)getModifier().withType(List.class);}
  public StructureModifier<WrappedDataWatcher> getDataWatcherModifier(){return getModifier().withType(WrappedDataWatcher.class);}
  public StructureModifier<WrappedChatComponent> getChatComponents(){return getModifier().withType(WrappedChatComponent.class);}
  public StructureModifier<WrappedChatComponent[]> getChatComponentArrays(){return getModifier().withType(WrappedChatComponent[].class);}
  public StructureModifier<PotionEffectType> getEffectTypes(){return getModifier().withType(PotionEffectType.class);}
  public StructureModifier<EntityType> getEntityTypeModifier(){return getModifier().withType(EntityType.class);}
  public StructureModifier<Sound> getSoundEffects(){return getModifier().withType(Sound.class);}
  public StructureModifier<WrappedParticle<?>> getNewParticles(){return (StructureModifier)getModifier().withType(WrappedParticle.class);}
  public StructureModifier<MultiBlockChangeInfo[]> getMultiBlockChangeInfoArrays(){return getModifier().withType(MultiBlockChangeInfo[].class);}
  @SuppressWarnings("unchecked") public StructureModifier<Iterable<PacketContainer>> getPacketBundles(){return (StructureModifier)getModifier().withType(Iterable.class);}
  public StructureModifier<InternalStructure> getStructures(){return getModifier().withType(InternalStructure.class);}
  @SuppressWarnings("unchecked") public StructureModifier<Optional<InternalStructure>> getOptionalStructures(){return (StructureModifier)getModifier().withType(Optional.class);}
  public StructureModifier<CustomPacketPayloadWrapper> getCustomPacketPayloads(){return getModifier().withType(CustomPacketPayloadWrapper.class);}
  public <T> StructureModifier<T> getSpecificModifier(Class<T> clazz){return getModifier().withType(clazz);}
  public <T extends Enum<T>> StructureModifier<T> getEnumModifier(Class<T> clazz, Object ignored){return getModifier().withType(clazz);}
  public <T> StructureModifier<Optional<T>> getOptionals(EquivalentConverter<T> converter){return (StructureModifier)getModifier().withType(Optional.class);}
  public <T> StructureModifier<Set<T>> getSets(EquivalentConverter<T> converter){return (StructureModifier)getModifier().withType(Set.class);}
}

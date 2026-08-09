package com.comphenix.protocol.wrappers;
import com.comphenix.protocol.reflect.EquivalentConverter;
import org.bukkit.GameMode;
public final class EnumWrappers {
  private EnumWrappers(){}
  public enum PlayerDigType { START_DESTROY_BLOCK, ABORT_DESTROY_BLOCK, STOP_DESTROY_BLOCK, DROP_ALL_ITEMS, DROP_ITEM, RELEASE_USE_ITEM, SWAP_HELD_ITEMS }
  public enum Direction { DOWN, UP, NORTH, SOUTH, WEST, EAST }
  public enum Hand { MAIN_HAND, OFF_HAND }
  public enum EntityUseAction { INTERACT, ATTACK, INTERACT_AT }
  public enum PlayerAction { START_SNEAKING, STOP_SNEAKING, LEAVE_BED, START_SPRINTING, STOP_SPRINTING, START_RIDING_JUMP, STOP_RIDING_JUMP, OPEN_INVENTORY, START_FALL_FLYING }
  public enum PlayerInfoAction { ADD_PLAYER, UPDATE_GAME_MODE, UPDATE_LATENCY, UPDATE_DISPLAY_NAME, REMOVE_PLAYER }
  public enum NativeGameMode { NOT_SET, SURVIVAL, CREATIVE, ADVENTURE, SPECTATOR }
  public enum ItemSlot { MAINHAND, OFFHAND, FEET, LEGS, CHEST, HEAD, BODY }
  public enum ChatType { CHAT, SYSTEM, GAME_INFO }
  public enum SoundCategory { MASTER, MUSIC, RECORDS, WEATHER, BLOCKS, HOSTILE, NEUTRAL, PLAYERS, AMBIENT, VOICE }
  public static Class<?> getGameModeClass(){return GameMode.class;}
  public static EquivalentConverter<NativeGameMode> getGameModeConverter(){return enumConverter(NativeGameMode.class);}
  public static <T extends Enum<T>> EquivalentConverter<T> getGenericConverter(Class<T> c, Class<?> ignored){return enumConverter(c);}
  public static <T extends Enum<T>> EquivalentConverter<T> getGenericConverter(Class<T> c){return enumConverter(c);}
  private static <T extends Enum<T>> EquivalentConverter<T> enumConverter(Class<T> c){return new EquivalentConverter<T>(){public Object getGeneric(T s){return s==null?null:s.name();} public T getSpecific(Object o){if(o==null)return null;try{return Enum.valueOf(c,o instanceof Enum?((Enum<?>)o).name():String.valueOf(o));}catch(Exception e){return null;}}public Class<T> getSpecificType(){return c;}};}
}

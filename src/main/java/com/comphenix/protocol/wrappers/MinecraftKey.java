package com.comphenix.protocol.wrappers;
import com.comphenix.protocol.reflect.EquivalentConverter;
public final class MinecraftKey {
  private final String fullKey;
  public MinecraftKey(String key){this.fullKey=key;}
  public MinecraftKey(String prefix, String key){this.fullKey=prefix + ":" + key;}
  public String getFullKey(){return fullKey;} public String getKey(){int i=fullKey.indexOf(':');return i<0?fullKey:fullKey.substring(i+1);}
  public String getPrefix(){int i=fullKey.indexOf(':');return i<0?"minecraft":fullKey.substring(0,i);}
  public static MinecraftKey fromHandle(Object handle){return handle==null?null:new MinecraftKey(String.valueOf(handle));}
  public static EquivalentConverter<MinecraftKey> getConverter(){return new EquivalentConverter<MinecraftKey>(){public Object getGeneric(MinecraftKey k){return k==null?null:k.fullKey;} public MinecraftKey getSpecific(Object o){return MinecraftKey.fromHandle(o);} public Class<MinecraftKey> getSpecificType(){return MinecraftKey.class;}};}
  @Override public String toString(){return fullKey;}
}

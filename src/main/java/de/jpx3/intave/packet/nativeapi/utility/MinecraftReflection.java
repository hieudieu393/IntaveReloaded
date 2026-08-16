package de.jpx3.intave.packet.nativeapi.utility;
import de.jpx3.intave.klass.locate.Locate;
public final class MinecraftReflection {
  private static Class<?> locate(String key,String... fallbacks){try{Class<?> c=Locate.classByKey(key);if(c!=null)return c;}catch(Throwable ignored){}for(String n:fallbacks)try{return Class.forName(n);}catch(Throwable ignored){}throw new IllegalStateException("Unable to resolve Minecraft class "+key);}
  public static Class<?> getPacketDataSerializerClass(){return locate("PacketDataSerializer","net.minecraft.network.PacketDataSerializer","net.minecraft.network.FriendlyByteBuf");}
  public static Class<?> getBlockPositionClass(){return locate("BlockPosition","net.minecraft.core.BlockPosition");}
  public static Class<?> getGameProfileClass(){try{return Class.forName("com.mojang.authlib.GameProfile");}catch(Exception e){throw new RuntimeException(e);}}
  public static Class<?> getIChatBaseComponentClass(){return locate("IChatBaseComponent","net.minecraft.network.chat.IChatBaseComponent","net.minecraft.network.chat.Component");}
  public static Class<?> getPlayerInfoDataClass(){return locate("PlayerInfoData","net.minecraft.network.protocol.game.PacketPlayOutPlayerInfo$PlayerInfoData");}
  public static Class<?> getMinecraftKeyClass(){return locate("MinecraftKey","net.minecraft.resources.MinecraftKey","net.minecraft.resources.ResourceLocation");}
  public static boolean isBlockPosition(Object o){return o!=null && getBlockPositionClass().isInstance(o);}
}

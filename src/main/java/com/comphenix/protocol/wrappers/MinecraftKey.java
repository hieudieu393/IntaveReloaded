package com.comphenix.protocol.wrappers;
public final class MinecraftKey { private final String fullKey; public MinecraftKey(String key){this.fullKey=key;} public String getFullKey(){return fullKey;} public String getKey(){int i=fullKey.indexOf(':');return i<0?fullKey:fullKey.substring(i+1);} @Override public String toString(){return fullKey;} }

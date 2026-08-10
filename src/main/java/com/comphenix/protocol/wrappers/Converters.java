package com.comphenix.protocol.wrappers;
import com.comphenix.protocol.reflect.EquivalentConverter;
public final class Converters { public static <T> EquivalentConverter<T> passthrough(Class<T> c){return BukkitConverters.passthrough(c);} }

package de.jpx3.intave.packet.nativeapi.wrappers;
import de.jpx3.intave.packet.nativeapi.reflect.EquivalentConverter;
public final class Converters { public static <T> EquivalentConverter<T> passthrough(Class<T> c){return BukkitConverters.passthrough(c);} }

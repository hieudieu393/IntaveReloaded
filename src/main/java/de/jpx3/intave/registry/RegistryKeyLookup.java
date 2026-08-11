package de.jpx3.intave.registry;

import de.jpx3.intave.share.MinecraftKey;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.klass.locate.MethodSearchBySignature;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.util.Map;

public class RegistryKeyLookup {
  private final static Map<LookupKey, String> cache = new java.util.HashMap<>();

  public static String keyFrom(
    String iRegistryKey, Object value
  ) {
    if (!MinecraftVersions.VER1_14_0.atOrAbove()) {
      return "null";
    }
    LookupKey lookupKey = new LookupKey(iRegistryKey, value);
    return cache.computeIfAbsent(lookupKey, k -> {
      try {
        Object underlyingRegistry = keyFromValue.invoke(registryRegistry(), minecraftKeyFrom(iRegistryKey));
        Object object = keyFromValue.invoke(underlyingRegistry, value);
        return stringFromMinecraftKey(object);
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }
    });
  }

  public static class LookupKey {
    private final String iRegistryKey;
    private final Object value;

    public LookupKey(String iRegistryKey, Object value) {
      this.iRegistryKey = iRegistryKey;
      this.value = value;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      LookupKey lookupKey = (LookupKey) obj;
      return iRegistryKey.equals(lookupKey.iRegistryKey) && value.equals(lookupKey.value);
    }

    @Override
    public int hashCode() {
      int result = iRegistryKey.hashCode();
      result = 31 * result + value.hashCode();
      return result;
    }
  }

  private static final Class<?> iRegistryClass;
  private static final Class<?> iRegistryWritableClass;
  private static final MethodHandle keyFromValue;

  static {
    MethodHandle methodHandle = null;
    Class<?> iRegistry = null;
    Class<?> iRegistryWritable = null;
    try {
      iRegistry = Lookup.serverClass("IRegistry");
      if (MinecraftVersions.VER1_19_3.atOrAbove()) {
        iRegistry = Class.forName("net.minecraft.core.IRegistry");
      }
      iRegistryWritable = Class.forName(iRegistry.getName() + "Writable");
      methodHandle = MethodSearchBySignature
        .ofClass(iRegistry)
        .withParameters(new Class[]{Object.class})
        .withReturnType(Lookup.serverClass("MinecraftKey"))
        .enforceResult().search()
        .findFirstOrThrow();
    } catch (Throwable e) {
      e.printStackTrace();
    }
    keyFromValue = methodHandle;
    iRegistryClass = iRegistry;
    iRegistryWritableClass = iRegistryWritable;
  }

  private static Object minecraftKeyFrom(String fullKey) {
    return MinecraftKey.getConverter().getGeneric(new MinecraftKey(fullKey));
  }

  private static String stringFromMinecraftKey(Object key) {
    return MinecraftKey.fromHandle(key).getFullKey();
  }

  private static Object registryRegistry;

  private static Object registryRegistry() {
    if (registryRegistry == null) {
      registryRegistry = registryRegistryProvider();
    }
    return registryRegistry;
  }

  private static Object registryRegistryProvider() {
    Class<?> aClass = iRegistryWritableClass;
    if (aClass == null) {
      return null;
    }
    for (Field declaredField : Lookup.serverClass("IRegistry").getDeclaredFields()) {
      if (declaredField.getType() == aClass) {
        declaredField.setAccessible(true);
        try {
          return declaredField.get(null);
        } catch (IllegalAccessException e) {
          e.printStackTrace();
        }
      }
    }
    return null;
  }
}

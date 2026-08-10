package de.jpx3.intave.check;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * User-facing check naming modeled after common Matrix/NCP-style anti-cheat naming.
 * Internal check names and configuration keys are intentionally left untouched so existing
 * configs, thresholds and API integrations remain compatible.
 */
public final class CheckNames {
  private static final Map<String, String> CANONICAL_BY_INTERNAL;

  static {
    Map<String, String> names = new LinkedHashMap<>();
    names.put("physics", "Simulate");
    names.put("movementsignalguard", "Simulate");
    names.put("airstuckguard", "NoFall");
    names.put("groundspoofguard", "NoFall");
    names.put("elytrasignalguard", "Elytra");
    names.put("interactionraytrace", "Interact");
    names.put("heuristics", "KillAura");
    names.put("attackraytrace", "Reach");
    names.put("clickpatterns", "AutoClicker");
    names.put("clickspeedlimiter", "ClickSpeed");
    names.put("timer", "Timer");
    names.put("breakspeedlimiter", "FastBreak");
    names.put("protocolscanner", "BadPackets");
    names.put("placementanalysis", "Scaffold");
    names.put("inventoryclickanalysis", "Inventory");
    CANONICAL_BY_INTERNAL = Collections.unmodifiableMap(names);
  }

  private CheckNames() {
  }

  public static String canonicalFor(Check check) {
    return check == null ? "Unknown" : canonicalFor(check.name());
  }

  public static String canonicalFor(String internalName) {
    if (internalName == null || internalName.isEmpty()) {
      return "Unknown";
    }
    return CANONICAL_BY_INTERNAL.getOrDefault(internalName.toLowerCase(Locale.ROOT), internalName);
  }

  public static Map<String, String> mappings() {
    return CANONICAL_BY_INTERNAL;
  }
}

package de.jpx3.intave.check;

import de.jpx3.intave.IntavePlugin;

import java.util.Locale;

/**
 * Compatibility layer for user-facing sub-check toggles.
 *
 * Parent configuration keys remain unchanged (physics, heuristics, protocolscanner, ...), while
 * public Matrix/NCP-style names can be controlled below each parent's `signals` section.
 */
public final class CheckSignalConfiguration {
  private CheckSignalConfiguration() {
  }

  public static boolean enabled(String parentKey, String signalName) {
    if (parentKey == null || signalName == null) {
      return true;
    }
    String signal = signalName.toLowerCase(Locale.ROOT)
      .replace("_", "-")
      .replace(" ", "-");
    String path = "check." + parentKey.toLowerCase(Locale.ROOT) + ".signals." + signal;
    return IntavePlugin.singletonInstance().settings().getBoolean(path, true);
  }
}
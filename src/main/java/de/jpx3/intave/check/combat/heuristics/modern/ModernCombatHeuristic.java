package de.jpx3.intave.check.combat.heuristics.modern;

import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;

import java.util.Locale;

/**
 * Shared base for the modern combat analysis parts. These parts intentionally feed the existing
 * Heuristics violation pipeline instead of introducing a second combat-check subsystem.
 */
public abstract class ModernCombatHeuristic<M extends CheckCustomMetadata> extends MetaCheckPart<Heuristics, M> {
  protected ModernCombatHeuristic(Heuristics parentCheck, Class<? extends M> metaClass) {
    super(parentCheck, metaClass);
  }

  protected final void flag(User user, String rule, String details, double violationLevel) {
    Violation violation = Violation.builderFor(Heuristics.class)
      .forPlayer(user.player())
      .withCheckName(checkNameFor(rule))
      .withMessage("failed combat " + rule)
      .withDetails(details)
      .withVL(violationLevel)
      .withCustomThreshold("classic.thresholds")
      .build();
    Modules.violationProcessor().processViolation(violation);
  }

  private static String checkNameFor(String rule) {
    String normalized = rule == null ? "" : rule.toLowerCase(Locale.ROOT);
    return normalized.contains("rotation")
      || normalized.contains("look")
      || normalized.contains("aim")
      ? "Aim"
      : "KillAura";
  }
}

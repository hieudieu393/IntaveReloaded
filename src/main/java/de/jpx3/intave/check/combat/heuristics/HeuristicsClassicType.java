package de.jpx3.intave.check.combat.heuristics;

public enum HeuristicsClassicType {
  ATTACK_ACCURACY("attack-accuracy", "Aim"),
  ATTACK_REQUIRED("attack-required", "KillAura"),
  PRE_ATTACK("pre-attack", "KillAura"),
  ROTATION_ACCURACY("rotation-accuracy", "Aim"),
  ROTATION_EXACT("rotation-exact", "Aim"),
  ROTATION_SNAP("rotation-snap", "Aim"),
  ROTATION_SENSITIVITY("rotation-sensitivity", "Aim"),
  ROTATION_MODULO_RESET("rotation-reset", "Aim"),
  INVENTORY_ROTATIONS("inventory-rotations", "KillAura"),
  BLOCKING("blocking", "KillAura"),
  NO_SWING("no-swing", "KillAura"),
  SWING_ORDER("swing-order", "KillAura"),
  SPRINT_TOGGLES("sprint-toggles", "KillAura"),
  TOOL_SWITCH("tool-switch", "KillAura");

  private final String configurationName;
  private final String checkName;

  HeuristicsClassicType(String configurationName, String checkName) {
    this.configurationName = configurationName;
    this.checkName = checkName;
  }

  public String configurationName() {
    return configurationName;
  }

  public String verboseName() {
    return configurationName;
  }

  public String checkName() {
    return checkName;
  }
}

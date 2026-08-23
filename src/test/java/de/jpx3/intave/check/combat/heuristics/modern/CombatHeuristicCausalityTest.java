package de.jpx3.intave.check.combat.heuristics.modern;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatHeuristicCausalityTest {

  @Test
  void rotationModuloRequiresCombatEvidence() {
    assertFalse(CombatRotationHeuristic.shouldFlagRotationModulo(10.0F, 330.0F, 5.0F, false));
    assertTrue(CombatRotationHeuristic.shouldFlagRotationModulo(10.0F, 330.0F, 5.0F, true));
  }

  @Test
  void deferredTargetMovementInvalidatesLargeStateChanges() {
    assertTrue(CombatObstructionHeuristic.isDeferredTargetPositionStable(0.5D, 0.0D, 0.0D));
    assertFalse(CombatObstructionHeuristic.isDeferredTargetPositionStable(1.01D, 0.0D, 0.0D));
  }
}

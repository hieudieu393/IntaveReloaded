/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.block.physics;

import de.jpx3.intave.check.movement.physics.environment.MockSimulationEnvironment;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_21_4;
import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_21_5;
import static org.junit.jupiter.api.Assertions.*;

final class BubbleColumnPhysicsTest {
  @Test
  void legacyClientOnlyTreatsAirAsAnOpenSurface() {
    ProtocolMetadata protocol = protocol(VER_1_21_4);

    assertTrue(BubbleColumnPhysics.openSurfaceAbove(protocol, Material.AIR, false, false));
    assertFalse(BubbleColumnPhysics.openSurfaceAbove(protocol, Material.STONE, true, true));
  }

  @Test
  void modernClientRequiresAnEmptyCollisionShapeAndDryFluid() {
    ProtocolMetadata protocol = protocol(VER_1_21_5);

    assertTrue(BubbleColumnPhysics.openSurfaceAbove(protocol, Material.STONE, true, true));
    assertFalse(BubbleColumnPhysics.openSurfaceAbove(protocol, Material.AIR, true, false));
    assertFalse(BubbleColumnPhysics.openSurfaceAbove(protocol, Material.AIR, false, true));
  }

  @Test
  void submergedBubbleColumnResetsOnlyTheCandidateEnvironment() {
    MockSimulationEnvironment root = new MockSimulationEnvironment();
    root.addFallDistance(4.0D);
    SimulationEnvironment candidate = root.mutableView();

    Motion result = new BubbleColumnPhysics().enterBubbleColumn(
      candidate, false, 0.1D, 0.0D, -0.2D
    );

    assertEquals(4.0D, root.fallDistance(), 0.0D);
    assertEquals(0.0D, candidate.fallDistance(), 0.0D);
    assertEquals(0.06D, result.motionY(), 0.0D);

    candidate.commitTo(root);
    assertEquals(0.0D, root.fallDistance(), 0.0D);
  }

  private static ProtocolMetadata protocol(int protocolVersion) {
    User user = UserFactory.createFallback();
    user.meta().protocol().setProtocolVersion(protocolVersion);
    return user.meta().protocol();
  }
}

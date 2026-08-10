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

package de.jpx3.intave.check.movement.physics.simulator;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.user.meta.ProtocolMetadata;

import java.util.Arrays;
import java.util.List;

public final class Simulators {
  public static final Simulator PLAYER = new BaseSimulator();
  public static final Simulator ELYTRA = new ElytraSimulator();
  public static final Simulator HORSE = new HorseSimulator();
  public static final Simulator BOAT = new BoatSimulator();

  private static final List<Simulator> ALL_SIMULATORS = Arrays.asList(PLAYER, ELYTRA, HORSE, BOAT);

  public static List<Simulator> simulators() {
    return ALL_SIMULATORS;
  }

  public static Simulator selectFor(SimulationEnvironment environment) {
    ProtocolMetadata protocol = environment.user().meta().protocol();
    boolean clientVehicleMovement = MinecraftVersions.VER1_9_0.atOrAbove() && protocol.combatUpdate();
    if (environment.isInVehicle() && clientVehicleMovement) {
      Entity vehicle = environment.vehicle();
      return vehicle.typeData().isBoat() ? BOAT : HORSE;
    }
    boolean elytraMovement = environment.shouldHaveFallFlyingPose()
      && !environment.inWater()
      && !environment.inLava();
    if (elytraMovement) {
      return ELYTRA;
    }
    return PLAYER;
  }
}

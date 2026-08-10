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

import de.jpx3.intave.check.movement.physics.config.MovementConfiguration;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.diagnostic.timings.Timings;
import de.jpx3.intave.player.collider.Colliders;
import de.jpx3.intave.player.collider.complex.SimulationResult;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;
import org.bukkit.util.Vector;

import static de.jpx3.intave.share.ClientMath.cos;
import static de.jpx3.intave.share.ClientMath.sin;

final class ElytraSimulator extends BaseSimulator {
  @Override
  public Simulation simulateTick(
    User user, Motion motion,
    SimulationEnvironment environment,
    MovementConfiguration configuration
  ) {
    motion = motion.copy();
    Timings.CHECK_PHYSICS_SIMULATOR.start();
    Timings.CHECK_PHYSICS_SIMULATOR_ELYTRA.start();
    simulateJump(user, motion, environment, configuration);

    float rotationPitch = environment.rotationPitch();
    Vector lookVector = environment.lookVector();

    double positionX = environment.verifiedLastPositionX();
    double positionY = environment.verifiedLastPositionY();
    double positionZ = environment.verifiedLastPositionZ();

    float pitchRad = rotationPitch * 0.017453292F;
    double lookVectorX = lookVector.getX();
    double lookVectorZ = lookVector.getZ();
    double distance = Math.sqrt(lookVectorX * lookVectorX + lookVectorZ * lookVectorZ);
    double dist2 = Math.sqrt(motion.motionX * motion.motionX + motion.motionZ * motion.motionZ);
    double rotationVectorLength = Math.sqrt(lookVector.lengthSquared());
    float pitchCosine = cos(pitchRad);
    pitchCosine = (float) ((double) pitchCosine * (double) pitchCosine * Math.min(1.0D, rotationVectorLength / 0.4D));
    motion.motionY += environment.gravity() * (-1 + pitchCosine * 0.75);

    if (motion.motionY < 0.0D && distance > 0.0D) {
      double d2 = motion.motionY * -0.1D * (double) pitchCosine;
      motion.motionY += d2;
      motion.motionX += lookVectorX * d2 / distance;
      motion.motionZ += lookVectorZ * d2 / distance;
    }

    if (pitchRad < 0.0F && distance > 0.0D) {
      double d9 = dist2 * (double) (-sin(pitchRad)) * 0.04D;
      motion.motionY += d9 * 3.2D;
      motion.motionX += -lookVectorX * d9 / distance;
      motion.motionZ += -lookVectorZ * d9 / distance;
    }

    if (distance > 0.0D) {
      motion.motionX += (lookVectorX / distance * dist2 - motion.motionX) * 0.1D;
      motion.motionZ += (lookVectorZ / distance * dist2 - motion.motionZ) * 0.1D;
    }

    motion.motionX *= 0.99f;
    motion.motionY *= 0.98f;
    motion.motionZ *= 0.99f;

    SimulationResult collisionResult = Colliders.collision(
      user, environment, motion, environment.inWeb(),
      positionX, positionY, positionZ
    );

    Timings.CHECK_PHYSICS_SIMULATOR_ELYTRA.stop();
    Timings.CHECK_PHYSICS_SIMULATOR.stop();
    return Simulation.of(user, configuration, environment, collisionResult);
  }

  @Override
  public Motion simulateAfterTick(User user, SimulationEnvironment environment, MovementConfiguration configuration, Position position, Motion motion) {
//    if (environment.simulationResult().onGround()) {
//      if (user.meta().movement().elytraFlying) {
//        user.meta().movement().elytraFlying = false;
//        if (IntaveControl.DEBUG_ELYTRA) {
//          user.player().sendMessage(ChatColor.RED + "Deactivated elytra flying because the player is on the ground");
//        }
//      }
//    }
    return super.simulateAfterTick(user, environment, configuration, position, motion);
  }

  @Override
  public boolean affectedByMovementKeys() {
    return false;
  }
}

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

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.fluid.Fluid;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.variant.BlockVariant;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.Material;

import java.util.Collections;
import java.util.List;

final class BubbleColumnPhysics implements BlockPhysic {
  private Material bubbleColumnBlock;

  @Override
  public void setupFor(MinecraftVersion serverVersion) {
    bubbleColumnBlock = Material.getMaterial("BUBBLE_COLUMN");
  }

  @Override
  public boolean supportedOnServerVersion() {
    return bubbleColumnBlock != null;
  }

  @Override
  public Motion entityInside(
    User user, SimulationEnvironment environment,
    BlockPosition blockPosition,
    Position from, Motion motion,
    boolean insideBlockOrTooFast
  ) {
    ProtocolMetadata protocol = user.meta().protocol();
    if (protocol.aquaticUpdate() && insideBlockOrTooFast) {
      BlockPosition above = blockPosition.above();
      Material materialAbove = VolatileBlockAccess.typeAccess(user, above);
      Fluid fluidAbove = VolatileBlockAccess.fluidAccess(user, above);
      BlockShape collisionShapeAbove = VolatileBlockAccess.collisionShapeAccess(user, above);
      boolean openSurface = openSurfaceAbove(
        protocol, materialAbove, fluidAbove.isDry(), collisionShapeAbove.isEmpty()
      );
      BlockVariant variant = VolatileBlockAccess.variantAccess(user, blockPosition);
      boolean downwards = variant.propertyOf("drag");
      if (openSurface) {
        return enterBubbleColumnWithAirAbove(downwards, motion.motionX, motion.motionY, motion.motionZ);
      } else {
        return enterBubbleColumn(environment, downwards, motion.motionX, motion.motionY, motion.motionZ);
      }
    }
    return null;
  }

  static boolean openSurfaceAbove(
    ProtocolMetadata protocol,
    Material materialAbove,
    boolean fluidAboveIsDry,
    boolean collisionShapeAboveIsEmpty
  ) {
    if (protocol.bubbleColumnSurfaceUsesCollisionAndFluid()) {
      return collisionShapeAboveIsEmpty && fluidAboveIsDry;
    }
    String materialName = materialAbove.name();
    return materialAbove == Material.AIR
      || materialName.equals("CAVE_AIR")
      || materialName.equals("VOID_AIR");
  }

  Motion enterBubbleColumn(
    SimulationEnvironment environment,
    boolean downwards,
    double motionX,
    double motionY,
    double motionZ
  ) {
    if (downwards) {
      motionY = Math.max(-0.3D, motionY - 0.03D);
    } else {
      motionY = Math.min(0.7D, motionY + 0.06D);
    }
    environment.resetFallDistance();
    return new Motion(motionX, motionY, motionZ);
  }

  private Motion enterBubbleColumnWithAirAbove(boolean downwards, double motionX, double motionY, double motionZ) {
    if (downwards) {
      motionY = Math.max(-0.9D, motionY - 0.03D);
    } else {
      motionY = Math.min(1.8D, motionY + 0.1D);
    }
	  return new Motion(motionX, motionY, motionZ);
  }

  @Override
  public List<Material> applicableMaterials() {
    return Collections.singletonList(bubbleColumnBlock);
  }
}

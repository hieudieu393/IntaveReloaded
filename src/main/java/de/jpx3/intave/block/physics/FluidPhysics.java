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

import com.google.common.collect.ImmutableList;
import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.fluid.Fluid;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

final class FluidPhysics implements BlockPhysic {
  private List<Material> materials;

  @Override
  public void setupFor(MinecraftVersion serverVersion) {
    List<Material> materials = new ArrayList<>();
    Material stationaryLava = Material.getMaterial("STATIONARY_LAVA");
    if (stationaryLava != null) {
      materials.add(stationaryLava);
    }
    materials.add(Material.LAVA);
    this.materials = ImmutableList.copyOf(materials);
  }

  @Override
  public Motion entityInside(User user, SimulationEnvironment environment, BlockPosition location, Position from, Motion motion, boolean insideBlockOrTooFast) {
    ProtocolMetadata protocol = user.meta().protocol();
    if (protocol.aquaticUpdate()) {
      Fluid fluid = VolatileBlockAccess.fluidAccess(user, location);
      if (fluid.isOfLava()) {
        if (protocol.fluidHeightBasedLavaMovement()) {
          updateLavaDepth(user, environment, protocol, location, fluid);
        } else {
          environment.setInLava(true);
        }
      }
    }
    return null;
  }

  private void updateLavaDepth(
    User user,
    SimulationEnvironment environment,
    ProtocolMetadata protocol,
    BlockPosition location,
    Fluid fluid
  ) {
    BoundingBox entityBox = environment.boundingBox();
    BoundingBox interactionBox = entityBox.shrink(0.001D);
    int blockX = location.getX();
    int blockY = location.getY();
    int blockZ = location.getZ();
    if (interactionBox.maxX <= blockX || interactionBox.minX >= blockX + 1.0D
      || interactionBox.maxY <= blockY || interactionBox.minY >= blockY + 1.0D
      || interactionBox.maxZ <= blockZ || interactionBox.minZ >= blockZ + 1.0D) {
      return;
    }

    Fluid fluidAbove = VolatileBlockAccess.fluidAccess(user, blockX, blockY + 1, blockZ);
    float fluidHeight = fluid.similarTo(fluidAbove) ? 1.0F : fluid.height();
    double fluidSurfaceY = protocol.fluidHeightUsesDoublePrecision()
      ? blockY + (double) fluidHeight
      : (double) ((float) blockY + fluidHeight);
    if (fluidSurfaceY < interactionBox.minY) {
      return;
    }

    double depthBaseY = protocol.refreshesFluidStateAfterMove()
      ? entityBox.minY
      : interactionBox.minY;
    double lavaDepth = fluidSurfaceY - depthBaseY;
    if (lavaDepth > environment.lavaDepth()) {
      environment.setLavaDepth(lavaDepth);
    }
  }

  @Override
  public List<Material> applicableMaterials() {
    return materials;
  }
}

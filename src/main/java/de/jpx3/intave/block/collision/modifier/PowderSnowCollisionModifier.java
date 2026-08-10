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

package de.jpx3.intave.block.collision.modifier;

import de.jpx3.intave.block.collision.CollisionOrigin;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.user.User;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class PowderSnowCollisionModifier extends CollisionModifier {
  private static final BlockShape FALLING_SHAPE = BoundingBox.originFromX16(0.0, 0.0, 0.0, 16.0, 0.9F * 16.0, 16.0);
  private static final BlockShape POWDER_SNOW_FROM_ABOVE = BlockShapes.originCube();

  @Override
  public BlockShape modify(
    User user, SimulationEnvironment environment, BoundingBox userBox,
    int posX, int posY, int posZ, BlockShape shape, CollisionOrigin type
  ) {
    return collisionShape(user, environment, posX, posY, posZ);
  }

  public static BlockShape collisionShape(
    User user,
    SimulationEnvironment environment,
    int posX,
    int posY,
    int posZ
  ) {
    if (environment.fallDistance() > 2.5) {
      return FALLING_SHAPE.contextualized(posX, posY, posZ);
    }
    if (canWalkOnPowderSnow(user)
      && environment.verifiedLastPositionY() > (double) posY + 1 - 9.999999747378752E-6
      && !environment.isSneaking()) {
      return POWDER_SNOW_FROM_ABOVE.contextualized(posX, posY, posZ);
    } else {
      return BlockShapes.emptyShape();
    }
  }

  public static boolean canWalkOnPowderSnow(User user) {
    ItemStack boots = user.player().getInventory().getBoots();
    return boots != null && boots.getType() == Material.LEATHER_BOOTS;
  }

  @Override
  public boolean matches(Material material) {
    return material.name().contains("POWDER_SNOW");
  }
}

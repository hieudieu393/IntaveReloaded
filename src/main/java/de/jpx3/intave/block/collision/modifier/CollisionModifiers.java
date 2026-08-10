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

import com.google.common.collect.Maps;
import de.jpx3.intave.block.collision.CollisionOrigin;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.user.User;
import org.bukkit.Material;

import java.util.EnumSet;
import java.util.Map;

public final class CollisionModifiers {
  private static final Map<Material, CollisionModifier> repository = Maps.newEnumMap(Material.class);
  private static final EnumSet<Material> activeMaterials = EnumSet.noneOf(Material.class);

  public static void setup() {
    setup(ScaffoldingCollisionModifier.class);
    setup(CarpetCollisionModifier.class);
    setup(ShulkerCollisionModifier.class);
    setup(PowderSnowCollisionModifier.class);
    setup(PistonCollisionModifier.class);
    setup(DoorCollisionModifier.class);
  }

  private static void setup(Class<? extends CollisionModifier> modifierClass) {
    CollisionModifier modifier;
    try {
      modifier = modifierClass.newInstance();
    } catch (InstantiationException | IllegalAccessException | Error exception) {
      throw new IllegalStateException("Unable to load collision modifier " + modifierClass, exception);
    }
    for (Material value : Material.values()) {
      if (modifier.matches(value)) {
        repository.put(value, modifier);
        activeMaterials.add(value);
      }
    }
  }

  public static BlockShape modified(
    User user, SimulationEnvironment environment, BoundingBox boundingBox, Material type,
    int posX, int posY, int posZ, BlockShape shape, CollisionOrigin requestType
  ) {
    return repository.get(type).modify(user, environment, boundingBox, posX, posY, posZ, shape, requestType);
  }

  public static BlockShape imaginaryBlockShape(
    Material type, User user, SimulationEnvironment environment,
    int posX, int posY, int posZ, int data
  ) {
    return repository.get(type).imaginaryBlockShape(type, user, environment, posX, posY, posZ, data);
  }

  public static boolean isModified(Material type) {
    return activeMaterials.contains(type);
  }
}

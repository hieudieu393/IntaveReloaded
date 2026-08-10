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
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.Material;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_12;

final class BedPhysics implements BlockPhysic {
  private List<Material> materials;

  @Override
  public void setupFor(MinecraftVersion serverVersion) {
    if (serverVersion.isAtLeast(MinecraftVersions.VER1_12_0)) {
      materials = resolveBedMaterials();
    } else {
      Material legacyBed = Material.getMaterial("BED_BLOCK");
      materials = legacyBed == null
        ? Collections.emptyList()
        : Collections.singletonList(legacyBed);
    }
  }

  @Override
  public Motion landed(User user, SimulationEnvironment environment, double motionX, double motionY, double motionZ) {
    MetadataBundle meta = user.meta();
    ProtocolMetadata protocolMetadata = meta.protocol();
    if (protocolMetadata.protocolVersion() < VER_1_12) {
      return null;
    }
    if (motionY < 0.0) {
      motionY = -motionY * 0.66f;
    }
    return environment.isSneaking() ? null : new Motion(motionX, motionY, motionZ);
  }

  private List<Material> resolveBedMaterials() {
    return Arrays.stream(Material.values())
      .filter(this::bedMaterial)
      .collect(Collectors.toList());
  }

  private boolean bedMaterial(Material material) {
    return material.name().toLowerCase().contains("bed") && material != Material.BEDROCK;
  }

  @Override
  public List<Material> applicableMaterials() {
    return materials;
  }
}
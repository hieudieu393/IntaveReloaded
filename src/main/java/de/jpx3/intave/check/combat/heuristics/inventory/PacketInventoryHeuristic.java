/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 */
package de.jpx3.intave.check.combat.heuristics.inventory;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClientStatus;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AbilityMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.InventoryMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.TELEPORT;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.mitigate.AttackNerfStrategy.BURN_LONGER;
import static de.jpx3.intave.module.mitigate.AttackNerfStrategy.DMG_HIGH;

public final class PacketInventoryHeuristic extends ClassicHeuristic<PacketInventoryHeuristic.PacketInventoryMeta> {
  public PacketInventoryHeuristic(Heuristics parentCheck) { super(parentCheck, HeuristicsClassicType.INVENTORY_ROTATIONS, PacketInventoryMeta.class); }

  @PacketSubscription(priority = ListenerPriority.LOW, packetsIn = CLIENT_COMMAND)
  public void receiveInventoryOpen(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    WrapperPlayClientClientStatus wrapper = new WrapperPlayClientClientStatus((PacketReceiveEvent) event);
    if (wrapper.getAction() == WrapperPlayClientClientStatus.Action.OPEN_INVENTORY_ACHIEVEMENT) {
      PacketInventoryMeta meta = metaOf(user);
      meta.performedInventoryOpenOperation = true;
      meta.inventoryTicks = 0;
    }
  }

  @PacketSubscription(priority = ListenerPriority.LOW, packetsIn = CLOSE_WINDOW)
  public void receiveInventoryClose(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    PacketInventoryMeta meta = metaOf(user);
    ProtocolMetadata clientData = user.meta().protocol();
    AbilityMetadata abilityData = user.meta().abilities();
    if (abilityData.ignoringMovementPackets()) return;
    if (clientData.emptyFlyingPacketsAreExplicitlySent() && meta.inventoryTicks == 0 && meta.performedInventoryOpenOperation) {
      flag(player, "closed inventory too quickly (" + meta.inventoryTicks + ")");
      user.nerf(BURN_LONGER, nerfId);
      user.nerf(DMG_HIGH, nerfId);
    }
  }

  @PacketSubscription(priority = ListenerPriority.HIGH, packetsIn = {POSITION, POSITION_LOOK, FLYING, LOOK})
  public void receiveMovement(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    PacketInventoryMeta meta = metaOf(user);
    PacketTypeCommon type = event.getPacketType();
    boolean hasRotation = type == PacketType.Play.Client.PLAYER_ROTATION || type == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION;
    InventoryMetadata inventoryData = user.meta().inventory();
    SimulationEnvironment movementData = user.meta().movement();
    ProtocolMetadata clientData = user.meta().protocol();
    if (!clientData.emptyFlyingPacketsAreExplicitlySent() || movementData.isInVehicle()) return;
    boolean inventoryOpen = inventoryData.inventoryOpen();
    if (!inventoryOpen) meta.performedInventoryOpenOperation = false;
    if (inventoryOpen && hasRotation && movementData.ticksPast(TELEPORT) > 20 && !player.isInsideVehicle()) {
      if (meta.rotationsInInventory++ > 1) {
        flag(player, "sent rotations in inventory (" + meta.rotationsInInventory + " rotations)");
        user.nerf(AttackNerfStrategy.HT_LIGHT, nerfId);
      }
    }
    if (!inventoryOpen) meta.reset();
    if (meta.performedInventoryOpenOperation) meta.inventoryTicks++;
    else meta.inventoryTicks = 0;
  }

  public static final class PacketInventoryMeta extends CheckCustomMetadata {
    private int rotationsInInventory;
    private int inventoryTicks;
    private boolean performedInventoryOpenOperation;
    private void reset() { rotationsInInventory = 0; }
  }
}

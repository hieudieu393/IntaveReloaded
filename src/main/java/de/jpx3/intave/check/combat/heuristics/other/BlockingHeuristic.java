/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 */
package de.jpx3.intave.check.combat.heuristics.other;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.entity.datawatcher.DataWatcherAccess;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.packet.reader.BlockDigReader;
import de.jpx3.intave.packet.reader.BlockInteractionReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import de.jpx3.intave.user.meta.PunishmentMetadata;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.TELEPORT;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_9;

public final class BlockingHeuristic extends ClassicHeuristic<BlockingHeuristic.BlockingMeta> {
  public BlockingHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.BLOCKING, BlockingMeta.class);
  }

  @PacketSubscription(packetsIn = {ARM_ANIMATION, FLYING, LOOK, POSITION, POSITION_LOOK})
  public void receiveMovementAndSwingPacket(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    BlockingMeta meta = metaOf(user);
    SimulationEnvironment movementData = user.meta().movement();
    if (movementData.ticksPast(TELEPORT) == 0) return;
    if (event.getPacketType() != PacketType.Play.Client.ANIMATION) {
      meta.releasedItemAfterClientTick = false;
      meta.ticksBetweenBlockAndUnblock++;
    }
    if (meta.ventosFreundlicherBoolean) meta.clientTicksBetweenBlockingToggle++;
    meta.heldItemOperations = 0;
  }

  @PacketSubscription(packetsIn = {BLOCK_PLACE, BLOCK_DIG})
  public void receiveInteractionPacket(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    PunishmentMetadata punishmentData = user.meta().punishment();
    BlockingMeta meta = metaOf(user);
    if (!user.meta().protocol().emptyFlyingPacketsAreExplicitlySent()
      || user.meta().abilities().ignoringMovementPackets()
      || user.meta().movement().ticksPast(TELEPORT) < 10) return;

    if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
      BlockDigReader reader = PacketReaders.readerOf(event);
      try {
        if (reader.action() == DiggingAction.RELEASE_USE_ITEM) {
          meta.releasedItemAfterClientTick = true;
          meta.ventosFreundlicherBoolean = true;
          int ticksBetweenBlockAndUnblock = meta.ticksBetweenBlockAndUnblock;
          if (ticksBetweenBlockAndUnblock == 0) {
            flag(player, "unblocked too quickly (" + ticksBetweenBlockAndUnblock + ")");
            user.nerf(AttackNerfStrategy.BLOCKING, "block:speed");
            punishmentData.timeLastBlockCancel = System.currentTimeMillis();
            Synchronizer.synchronize(() -> DataWatcherAccess.setDataWatcherFlag(player, DataWatcherAccess.WATCHER_BLOCKING_ID, false));
          }
        }
      } finally {
        reader.release();
      }
      return;
    }

    BlockInteractionReader reader = PacketReaders.readerOf(event);
    try {
      Material itemType = user.meta().inventory().heldItemType();
      boolean sword = itemType != null && itemType.name().endsWith("_SWORD");
      if (meta.releasedItemAfterClientTick) {
        flag(player, "sent multiple blocking interactions per tick (" + itemType + ")");
        user.nerf(AttackNerfStrategy.BLOCKING, "block:multiple");
      }
      int clientTicksBetweenBlockingToggle = meta.clientTicksBetweenBlockingToggle;
      if (reader.enumDirection() == 255 && meta.ventosFreundlicherBoolean && sword) {
        meta.clientTicksBetweenBlockingToggle = 0;
        meta.ventosFreundlicherBoolean = false;
        if (clientTicksBetweenBlockingToggle == 0 && meta.acaBlockingVL < 20) {
          meta.acaBlockingVL++;
          if (meta.acaBlockingVL > 2) {
            flag(player, "sent too few packets between block-toggle packets (vl: " + meta.acaBlockingVL + ")");
            user.nerf(AttackNerfStrategy.BLOCKING, "block:packets");
          }
        } else if (meta.acaBlockingVL > 1) {
          meta.acaBlockingVL -= 2;
        }
      }
      meta.ticksBetweenBlockAndUnblock = 0;
    } finally {
      reader.release();
    }
  }

  @PacketSubscription(priority = ListenerPriority.HIGH, packetsIn = {FLYING, POSITION, POSITION_LOOK, LOOK, VEHICLE_MOVE})
  public void receiveMovementPacket(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    BlockingMeta meta = metaOf(user);
    SimulationEnvironment movementData = user.meta().movement();
    ProtocolMetadata clientData = user.meta().protocol();
    if (movementData.ticksPast(TELEPORT) < 10) return;
    if (!movementData.receivedFlyingPacketIn(2) || clientData.protocolVersion() < VER_1_9) {
      if (meta.heldItemOperations > 0 && (meta.blocksPlacedThisTick == 0 || meta.heldItemOperations > 2)) {
        String description = "sent too many item operations (operations: " + meta.heldItemOperations + ")";
        description += " (version " + user.meta().protocol().versionString() + ")";
        flag(player, description);
      }
    }
    meta.blocksPlacedThisTick = 0;
  }

  @PacketSubscription(packetsIn = USE_ITEM)
  public void receiveUseItem(ProtocolPacketEvent event) {
    User user = userOf(event.getPlayer());
    if (user.meta().protocol().protocolVersion() >= VER_1_9) metaOf(user).blocksPlacedThisTick++;
  }

  @PacketSubscription(packetsIn = BLOCK_PLACE)
  public void receiveBlockPlace(ProtocolPacketEvent event) {
    User user = userOf(event.getPlayer());
    if (user.meta().protocol().protocolVersion() < VER_1_9) metaOf(user).blocksPlacedThisTick++;
  }

  @PacketSubscription(packetsIn = HELD_ITEM_SLOT_IN)
  public void receiveHeldItemSlot(ProtocolPacketEvent event) {
    User user = userOf(event.getPlayer());
    if (!user.meta().abilities().ignoringMovementPackets()) metaOf(user).heldItemOperations++;
  }

  public static final class BlockingMeta extends CheckCustomMetadata {
    private int blocksPlacedThisTick;
    public boolean releasedItemAfterClientTick;
    public int ticksBetweenBlockAndUnblock, clientTicksBetweenBlockingToggle;
    public boolean ventosFreundlicherBoolean;
    public int acaBlockingVL;
    public int heldItemOperations;
  }
}

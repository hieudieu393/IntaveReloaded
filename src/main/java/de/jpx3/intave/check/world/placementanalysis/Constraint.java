/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 */
package de.jpx3.intave.check.world.placementanalysis;

import de.jpx3.intave.check.PlayerCheckPart;
import de.jpx3.intave.check.world.PlacementAnalysis;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.reader.BlockInteractionReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;

import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.TELEPORT;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class Constraint extends PlayerCheckPart<PlacementAnalysis> {
  private int backwardsStreak;
  private int lastBlockClicks;
  private int blockClicks;
  private int tickCount;

  public Constraint(User user, PlacementAnalysis parentCheck) {
    super(user, parentCheck);
  }

  @PacketSubscription(priority = ListenerPriority.HIGH, packetsIn = {FLYING, LOOK, POSITION, POSITION_LOOK})
  public void receiveMovementPacket(ProtocolPacketEvent event) {
    User user = userOf(event.getPlayer());
    MovementMetadata movement = user.meta().movement();
    if (movement.ticksPast(TELEPORT) == 0) return;
    int forward = movement.keyForward;
    int strafe = movement.keyStrafe;
    if (forward == -1 && strafe == 0) {
      backwardsStreak++;
    } else if (forward == 0 && strafe == 0) {
      backwardsStreak = Math.max(0, backwardsStreak - 4);
    } else {
      backwardsStreak = 0;
    }
    tickCount++;
    if (tickCount > 20) {
      tickCount = 0;
      lastBlockClicks = blockClicks;
      blockClicks = 0;
    }
  }

  @PacketSubscription(packetsIn = {USE_ITEM, BLOCK_PLACE}, priority = ListenerPriority.LOW)
  public void rightClick(User user, BlockInteractionReader reader) {
    if (reader.direction() == null) blockClicks++;
  }
}

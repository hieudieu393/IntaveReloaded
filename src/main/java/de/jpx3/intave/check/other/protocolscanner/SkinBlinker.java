/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 */
package de.jpx3.intave.check.other.protocolscanner;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.player.HumanoidArm;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientSettings;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.check.CheckPart;
import de.jpx3.intave.check.other.ProtocolScanner;
import de.jpx3.intave.math.Hypot;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.entity.Player;
import org.bukkit.inventory.MainHand;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.SETTINGS;

public final class SkinBlinker extends CheckPart<ProtocolScanner> {
  private static final boolean HAS_OFF_HAND = MinecraftVersions.VER1_9_0.atOrAbove();

  public SkinBlinker(ProtocolScanner parentCheck) {
    super(parentCheck);
  }

  @PacketSubscription(packetsIn = SETTINGS)
  public void receiveClientOptions(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    if (MinecraftVersions.VER1_20_2.atOrAbove()) return;

    ProtocolMetadata clientData = user.meta().protocol();
    if (HAS_OFF_HAND && clientData.combatUpdate()) {
      WrapperPlayClientSettings settings = new WrapperPlayClientSettings((PacketReceiveEvent) event);
      if (!equalHand(player.getMainHand(), settings.getMainHand())) return;
    }

    MovementMetadata movementData = user.meta().movement();
    int keyForward = movementData.keyForward;
    int keyStrafe = movementData.keyStrafe;
    double distanceMoved = Hypot.fast(movementData.offsetMotionX(), movementData.offsetMotionZ());
    if (movementData.inWeb || movementData.receivedFlyingPacketIn(2)) return;
    if ((keyForward != 0 || keyStrafe != 0) && distanceMoved > 0.1) {
      event.setCancelled(true);
    }
  }

  private boolean equalHand(MainHand bukkitHand, HumanoidArm hand) {
    return bukkitHand == MainHand.LEFT && hand == HumanoidArm.LEFT
      || bukkitHand == MainHand.RIGHT && hand == HumanoidArm.RIGHT;
  }
}

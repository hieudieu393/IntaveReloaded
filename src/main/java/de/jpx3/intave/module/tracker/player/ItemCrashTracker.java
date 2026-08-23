/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 */
package de.jpx3.intave.module.tracker.player;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientUpdateSign;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.reader.WindowItemReader;
import de.jpx3.intave.player.FaultKicks;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.InventoryMetadata;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.UPDATE_SIGN;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.WINDOW_CLICK;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.SET_SLOT;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.WINDOW_ITEMS;

public final class ItemCrashTracker extends Module {
  @PacketSubscription(packetsOut = {WINDOW_ITEMS, SET_SLOT})
  public void checkOutgoingItems(User user, WindowItemReader reader) {
    for (ItemStack stack : reader.itemMap().values()) {
      if (stack != null) putOnWhitelist(user, stack);
    }
  }

  private void putOnWhitelist(User user, ItemStack stack) {
    String name = ownerFromSkull(stack);
    if (name != null) user.meta().inventory().registerSkullRequest(name);
  }

  private String ownerFromSkull(ItemStack skull) {
    String name = skull.getType().name();
    if (!(name.contains("SKULL") || name.contains("HEAD"))) return null;
    ItemMeta meta = skull.getItemMeta();
    return meta instanceof SkullMeta ? ((SkullMeta) meta).getOwner() : null;
  }

  @PacketSubscription(packetsIn = UPDATE_SIGN)
  public void checkSign(ProtocolPacketEvent event) {
    if (!(event instanceof PacketReceiveEvent)) return;
    User user = UserRepository.userOf((Player) event.getPlayer());
    String[] lines = new WrapperPlayClientUpdateSign((PacketReceiveEvent) event).getTextLines();
    if (lines == null) return;
    for (String line : lines) {
      if (line != null && line.length() > 500) {
        event.setCancelled(true);
        user.kick("Too many characters in sign update packet");
        return;
      }
    }
  }

  @PacketSubscription(packetsIn = {WINDOW_CLICK})
  public void windowClickCrashFix(ProtocolPacketEvent event) {
    User user = UserRepository.userOf((Player) event.getPlayer());
    InventoryMetadata inventoryData = user.meta().inventory();
    if (System.currentTimeMillis() - inventoryData.lastWCCReset > 10000) {
      inventoryData.windowClickCounter = 0;
      inventoryData.lastWCCReset = System.currentTimeMillis();
    }
    if (inventoryData.windowClickCounter++ > 500 && FaultKicks.INVENTORY_FAULTS) {
      user.kick("Too many inventory interactions");
      event.setCancelled(true);
    }
  }
}

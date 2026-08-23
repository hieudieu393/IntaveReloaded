package de.jpx3.intave.module.tracker.player;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClientStatus;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow;
import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.block.type.BlockTypeAccess;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketId;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.reader.WindowItemReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.InventoryMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.CLIENT_COMMAND;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.*;

public final class InventoryTracker extends Module {
  @PacketSubscription(priority = ListenerPriority.HIGH, packetsOut = {OPEN_WINDOW}, ignoreCancelled = false)
  public void sentOpenInventory(ProtocolPacketEvent event) {
    if (!(event instanceof PacketSendEvent)) return;
    Player player = (Player) event.getPlayer();
    User user = UserRepository.userOf(player);
    InventoryMetadata inventoryData = user.meta().inventory();
    WrapperPlayServerOpenWindow wrapper = new WrapperPlayServerOpenWindow((PacketSendEvent) event);
    boolean clientDoesNotSendCloseWindow = String.valueOf(wrapper.getTitle()).contains("container.beacon");

    if (!clientDoesNotSendCloseWindow) {
      Modules.feedback().synchronize(player, null, (p, x) -> openInventory(p));
      inventoryData.forceInventoryOnClickOpen = true;
    } else {
      inventoryData.forceInventoryOnClickOpen = false;
    }
  }

  @PacketSubscription(priority = ListenerPriority.LOW, packetsIn = {CLIENT_COMMAND})
  public void receiveClientCommand(ProtocolPacketEvent event) {
    if (!(event instanceof PacketReceiveEvent)) return;
    WrapperPlayClientClientStatus wrapper = new WrapperPlayClientClientStatus((PacketReceiveEvent) event);
    if (wrapper.getAction() == WrapperPlayClientClientStatus.Action.OPEN_INVENTORY_ACHIEVEMENT) {
      openInventory((Player) event.getPlayer());
    }
  }

  private void openInventory(Player player) {
    User user = UserRepository.userOf(player);
    InventoryMetadata inventoryData = user.meta().inventory();
    if (!inNetherPortal(user)) inventoryData.updateInventoryOpenState(true);
  }

  private boolean inNetherPortal(User user) {
    MovementMetadata movementData = user.meta().movement();
    return Collision.rasterizedTypeSearch(user, movementData.boundingBox(), BlockTypeAccess.NETHER_PORTAL);
  }

  @PacketSubscription(priority = ListenerPriority.HIGH, packetsOut = {PacketId.Server.CLOSE_WINDOW}, ignoreCancelled = false)
  public void sentCloseInventory(ProtocolPacketEvent event) {
    Player player = (Player) event.getPlayer();
    Modules.feedback().synchronize(player, null, (p, x) -> closeInventory(p));
  }

  @PacketSubscription(priority = ListenerPriority.LOW, packetsIn = {PacketId.Client.CLOSE_WINDOW})
  public void receiveCloseWindow(ProtocolPacketEvent event) {
    closeInventory((Player) event.getPlayer());
  }

  private void closeInventory(Player player) {
    User user = UserRepository.userOf(player);
    user.meta().inventory().updateInventoryOpenState(false);
  }

  @PacketSubscription(priority = ListenerPriority.HIGH, packetsOut = {RESPAWN}, ignoreCancelled = false)
  public void sentRespawn(ProtocolPacketEvent event) {
    Player player = (Player) event.getPlayer();
    UserRepository.userOf(player).meta().inventory().updateInventoryOpenState(false);
  }

  @PacketSubscription(packetsOut = {WINDOW_ITEMS})
  public void on(User user, WindowItemReader reader) {
    if (reader.windowId() == 0) {
      List<String> collect = reader.itemMap().values().stream()
        .map(itemStack -> itemStack.getType().name()).collect(Collectors.toList());
      user.tickFeedback(() -> user.meta().inventory().setItems(collect));
    }
  }
}

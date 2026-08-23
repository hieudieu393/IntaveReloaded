/* Copyright 2026 Intave */
package de.jpx3.intave.packet.reader;

import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class WindowClickReader extends AbstractPacketReader {
  private WrapperPlayClientClickWindow wrapper;

  @Override
  protected void read() {
    wrapper = new WrapperPlayClientClickWindow(receiveEvent());
  }

  public InventoryClickType clickType() {
    WrapperPlayClientClickWindow.WindowClickType type = wrapper.getWindowClickType();
    try {
      return InventoryClickType.valueOf(type.name());
    } catch (IllegalArgumentException ignored) {
      return InventoryClickType.UNKNOWN;
    }
  }

  public int containerId() { return wrapper.getWindowId(); }

  public String clickedItemTypeIfPossible(Player player) {
    if (containerId() == 0 && slot() >= 0) {
      User user = UserRepository.userOf(player);
      List<String> items = user.meta().inventory().items();
      int slot = slot();
      return items == null || slot >= items.size() ? null : items.get(slot);
    }
    return null;
  }

  public int slot() { return wrapper.getSlot(); }
  public int button() { return wrapper.getButton(); }
  public int actionNumber() { return wrapper.getActionNumber().orElse(-1); }

  /** Bukkit item conversion is intentionally not guessed for 1.21.5+ hashed stacks. */
  public ItemStack itemStack() { return null; }

  public boolean isDrop() { return clickType() == InventoryClickType.THROW && slot() != -999; }

  public boolean missingItemStack() {
    return clickType() == InventoryClickType.QUICK_MOVE || clickType() == InventoryClickType.SWAP;
  }

  @Override
  public void release() {
    wrapper = null;
    super.release();
  }

  public enum InventoryClickType {
    PICKUP, QUICK_MOVE, SWAP, CLONE, THROW, QUICK_CRAFT, PICKUP_ALL, UNKNOWN
  }
}

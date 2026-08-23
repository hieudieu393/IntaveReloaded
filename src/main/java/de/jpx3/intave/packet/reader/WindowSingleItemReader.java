package de.jpx3.intave.packet.reader;

import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public final class WindowSingleItemReader extends AbstractPacketReader implements WindowItemReader {
  private WrapperPlayServerSetSlot wrapper;

  @Override
  protected void read() {
    wrapper = new WrapperPlayServerSetSlot(sendEvent());
  }

  @Override
  public int windowId() {
    return wrapper.getWindowId();
  }

  @Override
  public Map<Integer, ItemStack> itemMap() {
    ItemStack item = SpigotConversionUtil.toBukkitItemStack(wrapper.getItem());
    int slot = wrapper.getSlot();
    Map<Integer, ItemStack> map = new HashMap<>();
    map.put(slot, item);
    return map;
  }

  @Override
  public void release() {
    wrapper = null;
    super.release();
  }
}

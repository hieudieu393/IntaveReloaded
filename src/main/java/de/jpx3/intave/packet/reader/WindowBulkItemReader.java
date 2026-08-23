package de.jpx3.intave.packet.reader;

import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

public final class WindowBulkItemReader extends AbstractPacketReader implements WindowItemReader {
  private WrapperPlayServerWindowItems wrapper;

  @Override
  protected void read() {
    wrapper = new WrapperPlayServerWindowItems(sendEvent());
  }

  @Override
  public int windowId() {
    return wrapper.getWindowId();
  }

  @Override
  public Map<Integer, ItemStack> itemMap() {
    Map<Integer, ItemStack> map = new java.util.HashMap<>();
    for (int i = 0; i < wrapper.getItems().size(); i++) {
      map.put(i, SpigotConversionUtil.toBukkitItemStack(wrapper.getItems().get(i)));
    }
    return map;
  }

  @Override
  public void release() {
    wrapper = null;
    super.release();
  }
}

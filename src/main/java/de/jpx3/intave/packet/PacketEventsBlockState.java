package de.jpx3.intave.packet;

import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import de.jpx3.intave.adapter.MinecraftVersions;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.block.Block;

/**
 * Version-safe Bukkit -> PacketEvents block-state conversion.
 *
 * <p>The project compiles against legacy Spigot fixtures where Block#getBlockData does not exist,
 * so modern BlockData access is intentionally reflective while legacy MaterialData remains direct.</p>
 */
public final class PacketEventsBlockState {
  private PacketEventsBlockState() {
  }

  public static WrappedBlockState from(Block block) {
    if (!MinecraftVersions.VER1_13_0.atOrAbove()) {
      return SpigotConversionUtil.fromBukkitMaterialData(block.getState().getData());
    }
    try {
      Object bukkitBlockData = block.getClass().getMethod("getBlockData").invoke(block);
      Class<?> blockDataClass = Class.forName("org.bukkit.block.data.BlockData");
      return (WrappedBlockState) SpigotConversionUtil.class
        .getMethod("fromBukkitBlockData", blockDataClass)
        .invoke(null, bukkitBlockData);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("Unable to convert Bukkit block data through PacketEvents", exception);
    }
  }
}

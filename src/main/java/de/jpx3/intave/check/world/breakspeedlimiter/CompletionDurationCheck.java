package de.jpx3.intave.check.world.breakspeedlimiter;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import de.jpx3.intave.block.access.BlockInteractionAccess;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.world.BreakSpeedLimiter;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.module.violation.ViolationContext;
import de.jpx3.intave.module.violation.ViolationProcessor;
import de.jpx3.intave.packet.PacketEventsBlockState;
import de.jpx3.intave.packet.PacketSender;
import de.jpx3.intave.packet.reader.BlockDigReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.reflect.access.ReflectiveEntityAccess;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.InventoryMetadata;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class CompletionDurationCheck extends MetaCheckPart<BreakSpeedLimiter, CompletionDurationCheck.BreakSpeedFinishMeta> {
  public CompletionDurationCheck(BreakSpeedLimiter parentCheck) { super(parentCheck, BreakSpeedFinishMeta.class); }

  @PacketSubscription(priority = ListenerPriority.LOW, packetsIn = {POSITION, POSITION_LOOK, LOOK, FLYING, VEHICLE_MOVE})
  public void tickUpdate(ProtocolPacketEvent event) {
    Player player = (Player) event.getPlayer();
    User user = userOf(player);
    BreakSpeedFinishMeta meta = metaOf(user);
    if (meta.balance > 0 && !event.isCancelled()) meta.balance -= 0.005;
  }

  @PacketSubscription(priority = ListenerPriority.LOW, packetsIn = BLOCK_DIG)
  public void receiveBlockAction(ProtocolPacketEvent event) {
    Player player = (Player) event.getPlayer();
    User user = userOf(player);
    BreakSpeedFinishMeta meta = metaOf(user);
    InventoryMetadata inventoryData = user.meta().inventory();
    ItemStack heldItem = inventoryData.heldItem();
    BlockDigReader reader = PacketReaders.readerOf(event);
    try {
      BlockPosition blockPosition = reader.nativeBlockPosition();
      DiggingAction digType = reader.action();
      if (blockPosition == null || digType == null) return;
      switch (digType) {
        case START_DIGGING:
          float blockDamage = BlockInteractionAccess.blockDamage(player, heldItem, blockPosition);
          meta.breakProcess = true;
          meta.breakProcessStartTime = System.currentTimeMillis();
          meta.curBlockDamageMP = blockDamage;
          meta.targetBlockPosition = blockPosition;
          meta.maximumBlockDamage = blockDamage;
          break;
        case FINISHED_DIGGING:
          if (meta.targetBlockPosition != null && !meta.targetBlockPosition.equals(blockPosition)) blockPosition = meta.targetBlockPosition;
          long requiredDuration = resolveMillisecondsOf(resolveBlockDamageOnGround(player, heldItem, blockPosition));
          long actualDuration = System.currentTimeMillis() - meta.breakProcessStartTime;
          long exceeded = Math.max(0, requiredDuration - actualDuration);
          if (exceeded > 100 && meta.balance++ >= 2) {
            ViolationProcessor violationProcessor = Modules.violationProcessor();
            Violation violation = Violation.builderFor(BreakSpeedLimiter.class).forPlayer(player)
              .withMessage("broke block too quickly")
              .withDetails(MathHelper.formatDouble(exceeded / 50d, 2) + " ticks faster than expected").withVL(10).build();
            ViolationContext context = violationProcessor.processViolation(violation);
            if (context.shouldCounterThreat()) {
              event.setCancelled(true);
              refreshBlocksAround(player, blockPosition.toLocation(player.getWorld()));
            }
          }
          meta.breakProcess = false;
          break;
        case CANCELLED_DIGGING:
          meta.breakProcessStartTime = System.currentTimeMillis();
          meta.curBlockDamageMP = 0f;
          meta.targetBlockPosition = null;
          meta.breakProcess = false;
          meta.maximumBlockDamage = Float.MIN_VALUE;
          break;
        default: break;
      }
    } finally { reader.release(); }
  }

  private void refreshBlocksAround(Player player, Location targetLocation) {
    Synchronizer.synchronize(() -> { player.updateInventory(); refreshBlock(player, targetLocation); });
  }

  private void refreshBlock(Player player, Location location) {
    if (!VolatileBlockAccess.isInLoadedChunk(location.getWorld(), location.getBlockX(), location.getBlockZ())) return;
    Block block = VolatileBlockAccess.blockAccess(location);
    PacketSender.sendServerPacket(player, new WrapperPlayServerBlockChange(
      new Vector3i(location.getBlockX(), location.getBlockY(), location.getBlockZ()), PacketEventsBlockState.from(block)));
  }

  private long resolveMillisecondsOf(float blockDamage) {
    if (blockDamage == 0) return 0;
    long time = 0; float cur = 0f; int countdown = 100;
    while (cur < 1f) { cur += blockDamage; time += 50; if (--countdown < 0) break; }
    return time;
  }

  private float resolveBlockDamageOnGround(Player player, ItemStack itemInHand, BlockPosition blockPosition) {
    boolean onGroundBefore = ReflectiveEntityAccess.onGround(player);
    ReflectiveEntityAccess.setOnGround(player, true);
    float blockDamage = BlockInteractionAccess.blockDamage(player, itemInHand, blockPosition);
    ReflectiveEntityAccess.setOnGround(player, onGroundBefore);
    return blockDamage;
  }

  public static final class BreakSpeedFinishMeta extends CheckCustomMetadata {
    public BlockPosition targetBlockPosition;
    public float curBlockDamageMP = 0f;
    public float maximumBlockDamage;
    public boolean breakProcess;
    public long breakProcessStartTime;
    public double balance;
  }
}

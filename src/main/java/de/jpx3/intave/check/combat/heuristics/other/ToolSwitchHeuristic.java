package de.jpx3.intave.check.combat.heuristics.other;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientHeldItemChange;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketId;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.packet.reader.BlockDigReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public class ToolSwitchHeuristic extends ClassicHeuristic<ToolSwitchHeuristic.ToolSwitchHeuristicMeta> {
  public ToolSwitchHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.TOOL_SWITCH, ToolSwitchHeuristicMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      POSITION, POSITION_LOOK, LOOK, FLYING, VEHICLE_MOVE
    }
  )
  public void receiveMovementPacket(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    ToolSwitchHeuristicMeta meta = metaOf(player);
    meta.ticksSinceLastBreak++;
    meta.ticksSinceLastStop++;
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      PacketId.Client.BLOCK_DIG
    }
  )
  public void receiveBlockBreakAction(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    BlockDigReader reader = PacketReaders.readerOf(event);
    DiggingAction digType = reader.action();
    ToolSwitchHeuristicMeta meta = metaOf(player);

    if (digType == DiggingAction.START_DIGGING) {
      meta.ticksSinceLastBreak = 0;
    } else if (digType == DiggingAction.FINISHED_DIGGING) {
      meta.ticksSinceLastStop = 0;
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      PacketId.Client.HELD_ITEM_SLOT_IN
    }
  )
  public void receiveHeldItemSlotChange(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    int currentSlot = user.meta().inventory().handSlot();
    int slot = new WrapperPlayClientHeldItemChange((com.github.retrooper.packetevents.event.PacketReceiveEvent) event).getSlot();
    ToolSwitchHeuristicMeta meta = metaOf(player);

    if (meta.ticksSinceLastBreak <= 1) {
      meta.suspiciousBreakStart = true;
      meta.lastSlot = currentSlot;
    }

    if (meta.suspiciousBreakStart && meta.ticksSinceLastStop <= 1 && meta.lastSlot == slot) {
      meta.suspiciousBreakStart = false;
      if (++meta.vl > 3) {
        flag(player, "sent suspicious slot packets while breaking blocks (" + meta.ticksSinceLastStop + " ticks)");
        if (++meta.cancelVl > 1) {
          user.nerf(AttackNerfStrategy.DMG_LIGHT, "205");
        }
        meta.vl = 0;
      }
    }
  }

  public static class ToolSwitchHeuristicMeta extends CheckCustomMetadata {
    public int ticksSinceLastBreak;
    public int ticksSinceLastStop;
    public int lastSlot;
    public boolean suspiciousBreakStart;
    public int vl;
    public int cancelVl;
  }
}

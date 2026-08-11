package de.jpx3.intave.check.other.inventoryclickanalysis;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.other.InventoryClickAnalysis;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.packet.reader.WindowClickReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;

import static de.jpx3.intave.module.linker.packet.ListenerPriority.LOW;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

/**
 * Detects repeated isolated offhand SWAP + CLOSE_WINDOW sequences without a client movement/tick
 * boundary. The signal is buffered because inventory packet timing can be affected by proxies and
 * transient skipped ticks.
 */
public final class AutoSwap extends MetaCheckPart<InventoryClickAnalysis, AutoSwap.Meta> {
  private static final int OFFHAND_BUTTON = 40;

  public AutoSwap(InventoryClickAnalysis parentCheck) {
    super(parentCheck, Meta.class);
  }

  @PacketSubscription(
    priority = LOW,
    ignoreCancelled = false,
    packetsIn = WINDOW_CLICK
  )
  public void receiveClick(ProtocolPacketEvent event) {
    User user = userOf(event.getPlayer());
    Meta meta = metaOf(user);
    WindowClickReader reader = PacketReaders.readerOf(event.getPacket());
    try {
      meta.clickCount++;
      if (reader.containerId() == 0
        && reader.clickType() == WindowClickReader.InventoryClickType.SWAP
        && reader.button() == OFFHAND_BUTTON
        && meta.clickCount == 1) {
        meta.pendingSwapSlot = reader.slot();
      } else {
        meta.pendingSwapSlot = -1;
      }
    } finally {
      reader.release();
    }
  }

  @PacketSubscription(
    priority = LOW,
    ignoreCancelled = false,
    packetsIn = {FLYING, LOOK, POSITION, POSITION_LOOK, CLIENT_TICK_END}
  )
  public void receiveTick(ProtocolPacketEvent event) {
    Meta meta = metaOf(userOf(event.getPlayer()));
    meta.lastTickAt = System.currentTimeMillis();
    if (meta.clickCount > 0) {
      meta.ticksSinceSessionStart++;
    } else if (meta.buffer > 0) {
      meta.buffer = Math.max(0.0, meta.buffer - 0.05);
    }
  }

  @PacketSubscription(
    priority = LOW,
    ignoreCancelled = false,
    packetsIn = CLOSE_WINDOW
  )
  public void receiveClose(ProtocolPacketEvent event) {
    User user = userOf(event.getPlayer());
    Meta meta = metaOf(user);

    if (meta.pendingSwapSlot != -1 && meta.clickCount == 1 && meta.ticksSinceSessionStart == 0) {
      long sinceTick = System.currentTimeMillis() - meta.lastTickAt;
      boolean timingUncertain = sinceTick > 80;
      meta.buffer += timingUncertain ? 0.5 : 1.0;

      if (meta.buffer >= 4.0) {
        Violation violation = Violation.builderFor(InventoryClickAnalysis.class)
          .forPlayer(event.getPlayer())
          .withCheckName("AutoSwap")
          .withMessage("might be using automated inventory swap")
          .withDetails("isolated offhand swap + close, slot=" + meta.pendingSwapSlot)
          .withVL(3)
          .build();
        Modules.violationProcessor().processViolation(violation);
        meta.buffer = Math.max(2.0, meta.buffer - 1.0);
      }
    } else {
      meta.buffer = Math.max(0.0, meta.buffer - 0.5);
    }

    meta.clickCount = 0;
    meta.pendingSwapSlot = -1;
    meta.ticksSinceSessionStart = 0;
  }

  public static final class Meta extends CheckCustomMetadata {
    private int clickCount;
    private int pendingSwapSlot = -1;
    private int ticksSinceSessionStart;
    private long lastTickAt;
    private double buffer;
  }
}

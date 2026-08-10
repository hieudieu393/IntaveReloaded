package de.jpx3.intave.check.other.inventoryclickanalysis;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.other.InventoryClickAnalysis;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketId;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.PacketTypes;
import de.jpx3.intave.packet.reader.WindowClickReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;

import java.util.HashMap;
import java.util.Map;

import static de.jpx3.intave.module.linker.packet.ListenerPriority.LOWEST;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.OPEN_WINDOW;

/**
 * Inventory protocol/state invariants layered on the existing movement/timing analyzers. This guard
 * deliberately does not score click speed; it verifies container ownership, open/close ordering,
 * special slot bounds and impossible per-tick click bursts while the existing DelayAnalyzer,
 * RegrDelayAnalyzer and OnMoveCheck continue to own timing/movement evidence.
 */
public final class InventoryStateGuard extends MetaCheckPart<InventoryClickAnalysis, InventoryStateGuard.Meta> {
  private static final int OUTSIDE_SLOT = -999;

  public InventoryStateGuard(InventoryClickAnalysis parentCheck) {
    super(parentCheck, Meta.class);
  }

  @PacketSubscription(priority = LOWEST, packetsOut = OPEN_WINDOW, ignoreCancelled = false)
  public void open(PacketEvent event) {
    Meta meta = metaOf(userOf(event.getPlayer()));
    if (event.delegate() instanceof PacketSendEvent) {
      WrapperPlayServerOpenWindow wrapper = new WrapperPlayServerOpenWindow((PacketSendEvent) event.delegate());
      meta.activeWindowId = wrapper.getContainerId();
      meta.serverWindowKnown = true;
      meta.closedThisTick = false;
      return;
    }

    Integer id = event.getPacket().getIntegers().readSafely(0);
    if (id != null) {
      meta.activeWindowId = id;
      meta.serverWindowKnown = true;
      meta.closedThisTick = false;
    }
  }

  @PacketSubscription(priority = LOWEST, packetsOut = PacketId.Server.CLOSE_WINDOW, ignoreCancelled = false)
  public void serverClose(PacketEvent event) {
    Meta meta = metaOf(userOf(event.getPlayer()));
    meta.activeWindowId = 0;
    meta.serverWindowKnown = true;
    meta.closedThisTick = true;
  }

  @PacketSubscription(priority = LOWEST, packetsIn = PacketId.Client.CLOSE_WINDOW, ignoreCancelled = false)
  public void clientClose(PacketEvent event) {
    User user = userOf(event.getPlayer());
    Meta meta = metaOf(user);
    Integer id = event.getPacket().getIntegers().readSafely(0);
    if (id != null && meta.serverWindowKnown && meta.activeWindowId > 0 && id != meta.activeWindowId) {
      score(user, meta, "close-window-id",
        "client closed window=" + id + " while active=" + meta.activeWindowId, 0.75D, 2.0D);
    }
    meta.activeWindowId = 0;
    meta.serverWindowKnown = true;
    meta.closedThisTick = true;
  }

  @PacketSubscription(priority = LOWEST, packetsIn = WINDOW_CLICK, ignoreCancelled = false)
  public void click(User user, WindowClickReader reader, PacketEvent event) {
    Meta meta = metaOf(user);
    int container = reader.containerId();
    int slot = reader.slot();
    int button = reader.button();
    WindowClickReader.InventoryClickType type = reader.clickType();

    if (container < 0) {
      score(user, meta, "negative-window-id", "window=" + container, 1.0D, 3.0D);
    }

    if (meta.serverWindowKnown && meta.activeWindowId > 0
      && container != meta.activeWindowId && container != 0) {
      score(user, meta, "inactive-window-click",
        "clicked window=" + container + " while active=" + meta.activeWindowId, 0.85D, 2.0D);
    }

    if (meta.closedThisTick && container != 0) {
      score(user, meta, "click-after-close",
        "clicked window=" + container + " after close in same client tick", 0.75D, 2.0D);
    }

    // Vanilla uses -999 as the outside/cursor drop slot. Values below that are not meaningful.
    // Other negative values are reserved by some old inventory paths, so keep the hard bound wide.
    if (slot < OUTSIDE_SLOT) {
      if (!event.isReadOnly()) {
        event.setCancelled(true);
      } else {
        event.setReadOnly(false);
        event.setCancelled(true);
      }
      flag(user, "invalid inventory slot", "slot=" + slot + ", type=" + type, 8.0D);
      return;
    }

    if (slot == OUTSIDE_SLOT && type == WindowClickReader.InventoryClickType.SWAP) {
      score(user, meta, "outside-swap", "SWAP used outside-slot -999, button=" + button, 1.0D, 3.0D);
    }

    meta.clicksThisTick++;
    // A high threshold makes this independent from click-speed heuristics while catching packet
    // fabrication/burst automation. Normal shift-clicking never approaches this in one client tick.
    if (meta.clicksThisTick > 12 && stable(user)) {
      score(user, meta, "inventory-burst",
        "clicks=" + meta.clicksThisTick + " in one client tick", 0.5D, 2.0D);
    }

    if (type != null) {
      int count = meta.clickTypes.getOrDefault(type.name(), 0) + 1;
      meta.clickTypes.put(type.name(), count);
      if (count > 8 && stable(user)) {
        score(user, meta, "repeated-click-mode",
          type.name() + " count=" + count + " in one client tick", 0.4D, 1.5D);
      }
    }
  }

  @PacketSubscription(
    priority = LOWEST,
    packetsIn = {FLYING, LOOK, POSITION, POSITION_LOOK, CLIENT_TICK_END},
    ignoreCancelled = false
  )
  public void boundary(PacketEvent event) {
    User user = userOf(event.getPlayer());
    PacketType type = event.getPacketType();
    boolean modern = user.meta().protocol().sendsClientTickEnd();
    boolean boundary = modern ? PacketTypes.isClientEndTick(type) : !PacketTypes.isClientEndTick(type);
    if (!boundary) return;

    Meta meta = metaOf(user);
    meta.clicksThisTick = 0;
    meta.clickTypes.clear();
    meta.closedThisTick = false;
    for (Map.Entry<String, Double> entry : meta.buffers.entrySet()) {
      entry.setValue(Math.max(0.0D, entry.getValue() - 0.04D));
    }
  }

  private void score(User user, Meta meta, String rule, String details, double weight, double vl) {
    if (user.meta().movement().awaitTeleport || user.meta().movement().expectTeleport
      || user.meta().movement().inRespawnScreen) {
      return;
    }

    double reliability = stable(user) ? 1.0D : 0.5D;
    double next = meta.buffers.getOrDefault(rule, 0.0D) + weight * reliability;
    if (next < 2.0D) {
      meta.buffers.put(rule, next);
      return;
    }
    flag(user, rule, details, vl);
    meta.buffers.put(rule, 1.0D);
  }

  private void flag(User user, String rule, String details, double vl) {
    Violation violation = Violation.builderFor(InventoryClickAnalysis.class)
      .forPlayer(user.player())
      .withCheckName("Inventory")
      .withMessage("invalid inventory state")
      .withDetails(rule + ": " + details)
      .withVL(vl)
      .build();
    Modules.violationProcessor().processViolation(violation);
  }

  private static boolean stable(User user) {
    double average = user.meta().connection().averageMovementPacketTimestamp();
    return average > 0.0D && average <= 90.0D;
  }

  public static final class Meta extends CheckCustomMetadata {
    private final Map<String, Double> buffers = new HashMap<>();
    private final Map<String, Integer> clickTypes = new HashMap<>();
    private int activeWindowId;
    private boolean serverWindowKnown;
    private boolean closedThisTick;
    private int clicksThisTick;
  }
}

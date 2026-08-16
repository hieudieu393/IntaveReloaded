package de.jpx3.intave.check.other.inventoryclickanalysis;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.check.CheckSignalConfiguration;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.other.InventoryClickAnalysis;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketId;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.PacketTypes;
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.packet.reader.WindowClickReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.InventoryMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;

import java.util.HashMap;
import java.util.Map;

import static de.jpx3.intave.module.linker.packet.ListenerPriority.LOWEST;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.OPEN_WINDOW;

/**
 * Inventory protocol/state invariants layered on the existing movement/timing analyzers. This guard
 * deliberately does not score click speed; it verifies container ownership, open/close ordering,
 * action-vs-GUI state, special slot bounds and impossible per-tick click bursts while the existing
 * DelayAnalyzer/RegrDelayAnalyzer continue to own click timing evidence.
 */
public final class InventoryStateGuard extends MetaCheckPart<InventoryClickAnalysis, InventoryStateGuard.Meta> {
  private static final int OUTSIDE_SLOT = -999;

  public InventoryStateGuard(InventoryClickAnalysis parentCheck) {
    super(parentCheck, Meta.class);
  }

  @PacketSubscription(priority = LOWEST, packetsOut = OPEN_WINDOW, ignoreCancelled = false)
  public void open(ProtocolPacketEvent event) {
    Meta meta = metaOf(userOf(event.getPlayer()));
    if (event instanceof PacketSendEvent) {
      WrapperPlayServerOpenWindow wrapper = new WrapperPlayServerOpenWindow((PacketSendEvent) event);
      meta.activeWindowId = wrapper.getContainerId();
      meta.serverWindowKnown = true;
      meta.closedThisTick = false;
      meta.openGraceTicks = 2;
    }
  }

  @PacketSubscription(priority = LOWEST, packetsOut = PacketId.Server.CLOSE_WINDOW, ignoreCancelled = false)
  public void serverClose(ProtocolPacketEvent event) {
    Meta meta = metaOf(userOf(event.getPlayer()));
    meta.activeWindowId = 0;
    meta.serverWindowKnown = true;
    meta.closedThisTick = true;
    meta.openGraceTicks = 0;
  }

  @PacketSubscription(priority = LOWEST, packetsIn = PacketId.Client.CLOSE_WINDOW, ignoreCancelled = false)
  public void clientClose(ProtocolPacketEvent event) {
    User user = userOf(event.getPlayer());
    Meta meta = metaOf(user);
    if (!(event instanceof PacketReceiveEvent)) return;
    int id = new com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCloseWindow(
      (PacketReceiveEvent) event
    ).getWindowId();
    if (meta.serverWindowKnown && meta.activeWindowId > 0 && id != meta.activeWindowId) {
      score(user, meta, "close-window-id",
        "client closed window=" + id + " while active=" + meta.activeWindowId, 0.75D, 2.0D);
    }
    meta.activeWindowId = 0;
    meta.serverWindowKnown = true;
    meta.closedThisTick = true;
    meta.openGraceTicks = 0;
  }

  @PacketSubscription(priority = LOWEST, packetsIn = WINDOW_CLICK, ignoreCancelled = false)
  public void click(User user, WindowClickReader reader, ProtocolPacketEvent event) {
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

    if (container == 0 && user.meta().inventory().inventoryOpen()) {
      meta.openGraceTicks = Math.max(meta.openGraceTicks, 1);
    }

    if (slot < OUTSIDE_SLOT) {
      makeWritableAndCancel(event);
      flag(user, "Inventory", "invalid inventory slot", "slot=" + slot + ", type=" + type, 8.0D);
      return;
    }

    if (slot == OUTSIDE_SLOT && type == WindowClickReader.InventoryClickType.SWAP) {
      score(user, meta, "outside-swap", "SWAP used outside-slot -999, button=" + button, 1.0D, 3.0D);
    }

    meta.clicksThisTick++;
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

  @PacketSubscription(priority = LOWEST, packetsIn = {ATTACK_ENTITY, USE_ENTITY}, ignoreCancelled = false)
  public void attack(User user, EntityUseReader reader, ProtocolPacketEvent event) {
    if (!reader.isAttackPacket()) return;
    Meta meta = metaOf(user);
    if (!inventoryOpen(user, meta)) return;

    makeWritableAndCancel(event);
    score(user, meta, "attack-while-open", "attacked entity=" + reader.entityId() + " while inventory open",
      1.25D, 3.0D);
    closeInventory(user);
  }

  @PacketSubscription(priority = LOWEST, packetsIn = BLOCK_DIG, ignoreCancelled = false)
  public void dig(ProtocolPacketEvent event) {
    if (!(event instanceof PacketReceiveEvent)) return;
    WrapperPlayClientPlayerDigging wrapper = new WrapperPlayClientPlayerDigging((PacketReceiveEvent) event);
    if (wrapper.getAction() != DiggingAction.START_DIGGING) return;

    User user = userOf(event.getPlayer());
    Meta meta = metaOf(user);
    if (!inventoryOpen(user, meta)) return;

    makeWritableAndCancel(event);
    score(user, meta, "dig-while-open", "started digging while inventory open", 1.25D, 3.0D);
    closeInventory(user);
  }

  @PacketSubscription(priority = LOWEST, packetsIn = {BLOCK_PLACE, USE_ITEM_ON}, ignoreCancelled = false)
  public void place(ProtocolPacketEvent event) {
    User user = userOf(event.getPlayer());
    Meta meta = metaOf(user);
    if (!inventoryOpen(user, meta)) return;

    makeWritableAndCancel(event);
    score(user, meta, "place-while-open", "used/placed a block while inventory open", 1.25D, 3.0D);
    closeInventory(user);
  }

  @PacketSubscription(priority = LOWEST, packetsIn = ENTITY_ACTION_IN, ignoreCancelled = false)
  public void entityAction(ProtocolPacketEvent event) {
    if (!(event instanceof PacketReceiveEvent)) return;
    User user = userOf(event.getPlayer());
    Meta meta = metaOf(user);
    if (!inventoryOpen(user, meta) || user.meta().movement().awaitTeleport || user.meta().movement().expectTeleport) {
      return;
    }

    WrapperPlayClientEntityAction wrapper = new WrapperPlayClientEntityAction((PacketReceiveEvent) event);
    WrapperPlayClientEntityAction.Action action = wrapper.getAction();
    if (action == WrapperPlayClientEntityAction.Action.STOP_SNEAKING
      || action == WrapperPlayClientEntityAction.Action.STOP_SPRINTING) {
      return;
    }

    score(user, meta, "entity-action-while-open", "action=" + action, 0.75D, 2.0D);
    closeInventory(user);
  }

  @PacketSubscription(
    priority = LOWEST,
    packetsIn = {FLYING, LOOK, POSITION, POSITION_LOOK, CLIENT_TICK_END},
    ignoreCancelled = false
  )
  public void boundary(ProtocolPacketEvent event) {
    User user = userOf(event.getPlayer());
    PacketTypeCommon type = event.getPacketType();
    boolean modern = user.meta().protocol().sendsClientTickEnd();
    boolean boundary = modern ? PacketTypes.isClientEndTick(type) : !PacketTypes.isClientEndTick(type);
    if (!boundary) return;

    Meta meta = metaOf(user);
    boolean openNow = inventoryOpen(user, meta);
    if (openNow && !meta.lastInventoryOpen) {
      meta.openGraceTicks = Math.max(meta.openGraceTicks, 2);
    }

    checkMovementWhileOpen(user, meta);
    meta.lastInventoryOpen = openNow;
    meta.clicksThisTick = 0;
    meta.clickTypes.clear();
    meta.closedThisTick = false;
    if (meta.openGraceTicks > 0) meta.openGraceTicks--;
    for (Map.Entry<String, Double> entry : meta.buffers.entrySet()) {
      entry.setValue(Math.max(0.0D, entry.getValue() - 0.04D));
    }
  }

  private void checkMovementWhileOpen(User user, Meta meta) {
    if (!CheckSignalConfiguration.enabled("inventoryclickanalysis", "inventorymove")) {
      decayRule(meta, "movement-while-open", 0.25D);
      return;
    }
    if (!inventoryOpen(user, meta) || meta.openGraceTicks > 0 || !stable(user)) {
      decayRule(meta, "movement-while-open", 0.20D);
      return;
    }

    MovementMetadata movement = user.meta().movement();
    InventoryMetadata inventory = user.meta().inventory();
    if (movement.awaitTeleport || movement.expectTeleport || movement.inRespawnScreen
      || movement.isInVehicle() || movement.inWater || movement.inWeb || inventory.handActive()) {
      decayRule(meta, "movement-while-open", 0.25D);
      return;
    }

    boolean movingInput = movement.keyForward != 0 || movement.keyStrafe != 0;
    boolean jumping = movement.physicsJumped;
    if (!movingInput && !jumping) {
      decayRule(meta, "movement-while-open", 0.15D);
      return;
    }

    score(user, meta, "movement-while-open",
      "input=" + movement.keyForward + "/" + movement.keyStrafe + ", jumping=" + jumping,
      0.55D, 2.0D, "InventoryMove");
  }

  private boolean inventoryOpen(User user, Meta meta) {
    if (meta.closedThisTick) return false;
    return (meta.serverWindowKnown && meta.activeWindowId > 0) || user.meta().inventory().inventoryOpen();
  }

  private void score(User user, Meta meta, String rule, String details, double weight, double vl) {
    score(user, meta, rule, details, weight, vl, "Inventory");
  }

  private void score(User user, Meta meta, String rule, String details, double weight, double vl, String checkName) {
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
    flag(user, checkName, rule, details, vl);
    meta.buffers.put(rule, 1.0D);
  }

  private static void decayRule(Meta meta, String rule, double amount) {
    double value = meta.buffers.getOrDefault(rule, 0.0D);
    if (value > 0.0D) meta.buffers.put(rule, Math.max(0.0D, value - amount));
  }

  private void flag(User user, String checkName, String rule, String details, double vl) {
    Violation violation = Violation.builderFor(InventoryClickAnalysis.class)
      .forPlayer(user.player())
      .withCheckName(checkName)
      .withMessage("invalid inventory state")
      .withDetails(rule + ": " + details)
      .withVL(vl)
      .build();
    Modules.violationProcessor().processViolation(violation);
  }

  private static void makeWritableAndCancel(ProtocolPacketEvent event) {
    event.setCancelled(true);
  }

  private static void closeInventory(User user) {
    try {
      Synchronizer.synchronize(user.player()::closeInventory);
    } catch (Throwable ignored) {
    }
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
    private int openGraceTicks;
    private boolean lastInventoryOpen;
  }
}

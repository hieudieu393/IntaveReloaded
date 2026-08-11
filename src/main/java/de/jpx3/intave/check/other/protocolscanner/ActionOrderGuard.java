package de.jpx3.intave.check.other.protocolscanner;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAnimation;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.other.ProtocolScanner;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.PacketTypes;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static de.jpx3.intave.module.linker.packet.ListenerPriority.LOWEST;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

/**
 * Layered PacketOrder validation. ProtocolScanner still owns configuration/VL/mitigation while
 * this state machine independently verifies same-tick action conflicts, hand/entity interaction
 * pairing, swing order and native CLIENT_TICK_END ordering. Buffers are per-rule so unrelated
 * packet oddities cannot combine into a violation.
 */
public final class ActionOrderGuard extends MetaCheckPart<ProtocolScanner, ActionOrderGuard.Meta> {
  public ActionOrderGuard(ProtocolScanner parentCheck) {
    super(parentCheck, Meta.class);
  }

  @PacketSubscription(priority = LOWEST, packetsIn = ARM_ANIMATION, ignoreCancelled = false)
  public void animation(ProtocolPacketEvent event) {
    if (!(event instanceof PacketReceiveEvent)) return;
    WrapperPlayClientAnimation animation = new WrapperPlayClientAnimation((PacketReceiveEvent) event);
    if (animation.getHand() != InteractionHand.MAIN_HAND) return;

    Meta meta = metaOf(userOf(event.getPlayer()));
    meta.animationSinceAttack = true;
    meta.attackAwaitingPostSwing = false;
  }

  @PacketSubscription(priority = LOWEST, packetsIn = {ATTACK_ENTITY, USE_ENTITY}, ignoreCancelled = false)
  public void interact(ProtocolPacketEvent event) {
    User user = userOf(event.getPlayer());
    Meta meta = metaOf(user);
    String packetName = packetName(event.getPacketType());

    if (isName(packetName, "ATTACK", "ATTACK_ENTITY")) {
      onAttack(user, meta);
      markAfterInput(user, meta, "attack");
      markPostMovement(user, meta, "attack");
      return;
    }
    if (!(event instanceof PacketReceiveEvent)) return;

    WrapperPlayClientInteractEntity interaction = new WrapperPlayClientInteractEntity((PacketReceiveEvent) event);
    WrapperPlayClientInteractEntity.InteractAction action = interaction.getAction();
    if (action == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
      onAttack(user, meta);
      markAfterInput(user, meta, "attack");
      markPostMovement(user, meta, "attack");
      return;
    }

    InteractionHand hand = interaction.getHand();
    if (hand == null) hand = InteractionHand.MAIN_HAND;
    boolean sneaking = interaction.isSneaking().orElse(false);

    if (action == WrapperPlayClientInteractEntity.InteractAction.INTERACT_AT
      && user.protocolVersion() < ProtocolMetadata.VER_26_1_1) {
      meta.pendingInteractAt = true;
      meta.interactAtEntity = interaction.getEntityId();
      meta.interactAtHand = hand;
      meta.interactAtSneaking = sneaking;
    } else if (action == WrapperPlayClientInteractEntity.InteractAction.INTERACT
      && meta.pendingInteractAt && user.protocolVersion() < ProtocolMetadata.VER_26_1_1) {
      boolean matches = meta.interactAtEntity == interaction.getEntityId()
        && meta.interactAtHand == hand
        && meta.interactAtSneaking == sneaking;
      if (!matches) {
        conflict(user, meta, "interact-pair", "INTERACT_AT did not match INTERACT entity/hand/sneak", 0.85D, 2.0D);
      } else {
        decay(meta, "interact-pair", 0.25D);
      }
      meta.pendingInteractAt = false;
    }

    if (hand == InteractionHand.MAIN_HAND) {
      meta.mainInteractSet = true;
      meta.mainInteractEntity = interaction.getEntityId();
      meta.mainInteractAction = action;
      meta.mainInteractSneaking = sneaking;
    } else {
      if (!meta.mainInteractSet) {
        conflict(user, meta, "offhand-interact", "OFF_HAND interaction without preceding MAIN_HAND", 0.65D, 1.5D);
      } else {
        boolean matches = meta.mainInteractEntity == interaction.getEntityId()
          && meta.mainInteractAction == action
          && meta.mainInteractSneaking == sneaking;
        if (!matches) {
          conflict(user, meta, "offhand-interact", "MAIN/OFF_HAND entity interaction mismatch", 0.8D, 2.0D);
        } else {
          decay(meta, "offhand-interact", 0.2D);
        }
      }
      meta.mainInteractSet = false;
    }

    if (meta.release || meta.dig) {
      conflict(user, meta, "interact-conflict", "entity interaction after release/dig in same tick", 0.7D, 1.5D);
    }
    if (meta.useItem) {
      conflict(user, meta, "use-before-interact", "USE_ITEM before entity interaction in same tick", 0.65D, 1.5D);
    }

    meta.interact = true;
    markAfterInput(user, meta, "interact");
    markPostMovement(user, meta, "interact");
  }

  @PacketSubscription(priority = LOWEST, packetsIn = {BLOCK_PLACE, USE_ITEM, USE_ITEM_ON}, ignoreCancelled = false)
  public void use(ProtocolPacketEvent event) {
    User user = userOf(event.getPlayer());
    Meta meta = metaOf(user);
    boolean plainUse = isName(packetName(event.getPacketType()), "USE_ITEM");

    if (meta.attack && !meta.interact) {
      conflict(user, meta, "attack-use-order", "use after attack without an interaction pair", 0.8D, 2.0D);
    }
    if (meta.dig || meta.release || meta.drop) {
      conflict(user, meta, "use-conflict", "use/place conflicts with dig/release/drop in same tick", 0.7D, 1.5D);
    }
    if (!plainUse && meta.useItem) {
      conflict(user, meta, "use-before-place", "USE_ITEM before block interaction in same tick", 0.6D, 1.5D);
    }

    meta.use = true;
    if (plainUse) meta.useItem = true;
    else meta.place = true;
    markAfterInput(user, meta, plainUse ? "use-item" : "place");
    markPostMovement(user, meta, plainUse ? "use-item" : "place");
  }

  @PacketSubscription(priority = LOWEST, packetsIn = BLOCK_DIG, ignoreCancelled = false)
  public void dig(ProtocolPacketEvent event) {
    if (!(event instanceof PacketReceiveEvent)) return;
    User user = userOf(event.getPlayer());
    Meta meta = metaOf(user);
    WrapperPlayClientPlayerDigging dig = new WrapperPlayClientPlayerDigging((PacketReceiveEvent) event);
    DiggingAction action = dig.getAction();
    if (action == null) return;

    switch (action) {
      case RELEASE_USE_ITEM:
        if (meta.attack || meta.dig || meta.drop || meta.swap || meta.use) {
          conflict(user, meta, "release-conflict", "release-use conflicts with another same-tick action", 0.8D, 2.0D);
        }
        meta.release = true;
        meta.use = false;
        break;
      case DROP_ITEM:
      case DROP_ITEM_STACK:
        if (meta.attack || meta.use || meta.dig || meta.release || meta.swap || meta.pick) {
          conflict(user, meta, "drop-conflict", "drop conflicts with another same-tick action", 0.8D, 2.0D);
        }
        if (meta.inventoryClick || meta.swap) {
          conflict(user, meta, "drop-inventory-order", "drop after inventory/swap in same tick", 0.65D, 1.5D);
        }
        meta.drop = true;
        break;
      case SWAP_ITEM_WITH_OFFHAND:
        if (meta.attack || meta.use || meta.dig || meta.release || meta.drop || meta.pick) {
          conflict(user, meta, "swap-conflict", "offhand swap conflicts with another same-tick action", 0.8D, 2.0D);
        }
        meta.swap = true;
        break;
      case START_DIGGING:
      case CANCELLED_DIGGING:
      case FINISHED_DIGGING:
        if (meta.attack || meta.use || meta.release || meta.pick) {
          conflict(user, meta, "dig-conflict", "dig conflicts with attack/use/release/pick in same tick", 0.7D, 1.5D);
        }
        meta.dig = action == DiggingAction.START_DIGGING || meta.dig;
        if (action == DiggingAction.CANCELLED_DIGGING || action == DiggingAction.FINISHED_DIGGING) meta.dig = false;
        break;
      default:
        break;
    }

    markAfterInput(user, meta, "dig/" + action.name().toLowerCase(Locale.ROOT));
    markPostMovement(user, meta, "dig");
  }

  @PacketSubscription(priority = LOWEST, packetsIn = PICK_ITEM, ignoreCancelled = false)
  public void pick(ProtocolPacketEvent event) {
    User user = userOf(event.getPlayer());
    Meta meta = metaOf(user);
    if (meta.attack || meta.use || meta.release || meta.dig) {
      conflict(user, meta, "pick-conflict", "pick-item conflicts with combat/use/dig in same tick", 0.7D, 1.5D);
    }
    meta.pick = true;
    markAfterInput(user, meta, "pick-item");
    markPostMovement(user, meta, "pick-item");
  }

  @PacketSubscription(priority = LOWEST, packetsIn = HELD_ITEM_SLOT_IN, ignoreCancelled = false)
  public void heldSlot(ProtocolPacketEvent event) {
    User user = userOf(event.getPlayer());
    Meta meta = metaOf(user);
    if (meta.attack || meta.use || meta.release || meta.dig || meta.drop || meta.swap || meta.entityAction) {
      conflict(user, meta, "held-slot-order", "held-slot change conflicts with another action in same tick", 0.65D, 1.5D);
    }
    meta.heldSlot = true;
    markPostMovement(user, meta, "held-slot");
  }

  @PacketSubscription(priority = LOWEST, packetsIn = ENTITY_ACTION_IN, ignoreCancelled = false)
  public void entityAction(ProtocolPacketEvent event) {
    if (!(event instanceof PacketReceiveEvent)) return;
    User user = userOf(event.getPlayer());
    Meta meta = metaOf(user);
    WrapperPlayClientEntityAction wrapper = new WrapperPlayClientEntityAction((PacketReceiveEvent) event);
    WrapperPlayClientEntityAction.Action action = wrapper.getAction();
    if (action == null) return;

    boolean sneak = action == WrapperPlayClientEntityAction.Action.START_SNEAKING
      || action == WrapperPlayClientEntityAction.Action.STOP_SNEAKING;
    boolean sprint = action == WrapperPlayClientEntityAction.Action.START_SPRINTING
      || action == WrapperPlayClientEntityAction.Action.STOP_SPRINTING;

    if (sprint && user.protocolVersion() < ProtocolMetadata.VER_1_21_3 && meta.sneakToggle) {
      conflict(user, meta, "sprint-sneak-order", "sprint toggle after sneak toggle", 0.5D, 1.5D);
    }
    if (sneak && user.protocolVersion() >= ProtocolMetadata.VER_1_21_3 && meta.sprintToggle) {
      conflict(user, meta, "sprint-sneak-order", "sneak toggle after sprint toggle", 0.5D, 1.5D);
    }
    if ((sprint || sneak) && (meta.attack || meta.use || meta.dig || meta.release || meta.heldSlot)) {
      conflict(user, meta, "entity-action-order", "sprint/sneak toggle conflicts with action packet", 0.5D, 1.5D);
    }

    meta.entityAction = true;
    meta.sneakToggle |= sneak;
    meta.sprintToggle |= sprint;
    meta.inputToggleSeen |= sneak || sprint;
    markPostMovement(user, meta, "entity-action");
  }

  @PacketSubscription(priority = LOWEST, packetsIn = WINDOW_CLICK, ignoreCancelled = false)
  public void windowClick(ProtocolPacketEvent event) {
    if (!(event instanceof PacketReceiveEvent)) return;
    User user = userOf(event.getPlayer());
    Meta meta = metaOf(user);
    WrapperPlayClientClickWindow click = new WrapperPlayClientClickWindow((PacketReceiveEvent) event);
    WrapperPlayClientClickWindow.WindowClickType type = click.getWindowClickType();

    if (type == WrapperPlayClientClickWindow.WindowClickType.QUICK_MOVE) {
      if (meta.pickupClick) conflict(user, meta, "inventory-click-order", "QUICK_MOVE after PICKUP in same tick", 0.7D, 1.5D);
      meta.quickMoveClick = true;
    } else if (type == WrapperPlayClientClickWindow.WindowClickType.PICKUP
      || type == WrapperPlayClientClickWindow.WindowClickType.PICKUP_ALL) {
      if (meta.quickMoveClick) conflict(user, meta, "inventory-click-order", "PICKUP after QUICK_MOVE in same tick", 0.7D, 1.5D);
      meta.pickupClick = true;
    }

    if (meta.closeWindow) conflict(user, meta, "window-order", "inventory click after close-window in same tick", 0.75D, 1.5D);
    if (meta.drop) conflict(user, meta, "inventory-drop-order", "inventory click after drop in same tick", 0.6D, 1.5D);
    meta.inventoryClick = true;
    markPostMovement(user, meta, "window-click");
  }

  @PacketSubscription(priority = LOWEST, packetsIn = CLOSE_WINDOW, ignoreCancelled = false)
  public void close(ProtocolPacketEvent event) {
    User user = userOf(event.getPlayer());
    Meta meta = metaOf(user);
    if (meta.closeWindow) conflict(user, meta, "window-order", "duplicate close-window in same tick", 0.5D, 1.5D);
    if (meta.drop || meta.swap) conflict(user, meta, "window-close-order", "close-window after drop/swap in same tick", 0.55D, 1.5D);
    meta.closeWindow = true;
    markPostMovement(user, meta, "close-window");
  }

  @PacketSubscription(priority = LOWEST, packetsIn = {FLYING, LOOK, POSITION, POSITION_LOOK, CLIENT_TICK_END}, ignoreCancelled = false)
  public void boundary(ProtocolPacketEvent event) {
    User user = userOf(event.getPlayer());
    Meta meta = metaOf(user);
    PacketTypeCommon type = event.getPacketType();
    boolean hasTickEnd = user.meta().protocol().sendsClientTickEnd();

    if (hasTickEnd) {
      if (PacketTypes.isClientEndTick(type)) finishTick(user, meta, "CLIENT_TICK_END");
      else meta.movementSeen = true;
    } else {
      finishTick(user, meta, "movement");
    }
  }

  private void onAttack(User user, Meta meta) {
    if (!meta.animationSinceAttack || meta.attackAwaitingPostSwing) {
      conflict(user, meta, "attack-swing-order", "attack before required main-hand animation", 0.75D, 1.5D);
    }
    if (meta.use || meta.release || meta.dig || meta.pick || meta.drop || meta.swap) {
      conflict(user, meta, "attack-conflict", "attack conflicts with another same-tick action", 0.8D, 2.0D);
    }
    meta.attack = true;
    meta.attackAwaitingPostSwing = true;
    meta.animationSinceAttack = false;
  }

  private void finishTick(User user, Meta meta, String boundary) {
    if (meta.attackAwaitingPostSwing) {
      conflict(user, meta, "attack-swing-order", "attack not followed by animation before " + boundary, 0.65D, 1.5D);
    }
    resetTick(meta);
  }

  private void markAfterInput(User user, Meta meta, String action) {
    if (meta.inputToggleSeen) {
      conflict(user, meta, "input-action-order", action + " after sprint/sneak toggle in same tick", 0.5D, 1.5D);
    }
  }

  private void markPostMovement(User user, Meta meta, String action) {
    if (user.meta().protocol().sendsClientTickEnd() && meta.movementSeen) {
      conflict(user, meta, "post-movement-action", action + " after movement before CLIENT_TICK_END", 0.45D, 1.5D);
    }
  }

  private void conflict(User user, Meta meta, String rule, String details, double weight, double vl) {
    if (hardExempt(user)) return;

    double reliability = stable(user) ? 1.0D : 0.5D;
    double next = meta.buffers.getOrDefault(rule, 0.0D) + weight * reliability;
    double threshold = parentCheck().configuration().settings().doubleBy("packet-order-buffer", 2.0D);
    if (next < Math.max(1.5D, threshold)) {
      meta.buffers.put(rule, next);
      return;
    }

    int configuredVl = parentCheck().configuration().settings().intBy("packet-order-vl", (int) Math.ceil(vl));
    Violation violation = Violation.builderFor(ProtocolScanner.class)
      .forPlayer(user.player())
      .withCheckName("BadPackets")
      .withMessage("invalid packet order")
      .withDetails(rule + ": " + details)
      .withVL(Math.max(1, configuredVl))
      .build();
    Modules.violationProcessor().processViolation(violation);
    meta.buffers.put(rule, 1.0D);
  }

  private static void decay(Meta meta, String rule, double amount) {
    double value = meta.buffers.getOrDefault(rule, 0.0D);
    if (value > 0.0D) meta.buffers.put(rule, Math.max(0.0D, value - amount));
  }

  private static boolean stable(User user) {
    double average = user.meta().connection().averageMovementPacketTimestamp();
    return average > 0.0D && average <= 90.0D;
  }

  private static boolean hardExempt(User user) {
    return user.meta().movement().awaitTeleport
      || user.meta().movement().expectTeleport
      || user.meta().movement().inRespawnScreen
      || user.meta().movement().isInVehicle();
  }

  private static String packetName(PacketTypeCommon type) {
    return type == null || type.name() == null ? "" : type.name().toUpperCase(Locale.ROOT);
  }

  private static boolean isName(String actual, String... names) {
    for (String name : names) if (name.equalsIgnoreCase(actual)) return true;
    return false;
  }

  private static void resetTick(Meta meta) {
    meta.attack = false;
    meta.interact = false;
    meta.use = false;
    meta.useItem = false;
    meta.place = false;
    meta.release = false;
    meta.dig = false;
    meta.pick = false;
    meta.drop = false;
    meta.swap = false;
    meta.heldSlot = false;
    meta.entityAction = false;
    meta.sneakToggle = false;
    meta.sprintToggle = false;
    meta.inputToggleSeen = false;
    meta.inventoryClick = false;
    meta.pickupClick = false;
    meta.quickMoveClick = false;
    meta.closeWindow = false;
    meta.pendingInteractAt = false;
    meta.mainInteractSet = false;
    meta.movementSeen = false;
    meta.attackAwaitingPostSwing = false;
    meta.animationSinceAttack = true;
    for (Map.Entry<String, Double> entry : meta.buffers.entrySet()) {
      entry.setValue(Math.max(0.0D, entry.getValue() - 0.03D));
    }
  }

  public static final class Meta extends CheckCustomMetadata {
    private final Map<String, Double> buffers = new HashMap<>();
    private boolean attack;
    private boolean interact;
    private boolean use;
    private boolean useItem;
    private boolean place;
    private boolean release;
    private boolean dig;
    private boolean pick;
    private boolean drop;
    private boolean swap;
    private boolean heldSlot;
    private boolean entityAction;
    private boolean sneakToggle;
    private boolean sprintToggle;
    private boolean inputToggleSeen;
    private boolean inventoryClick;
    private boolean pickupClick;
    private boolean quickMoveClick;
    private boolean closeWindow;
    private boolean animationSinceAttack = true;
    private boolean attackAwaitingPostSwing;
    private boolean movementSeen;

    private boolean pendingInteractAt;
    private int interactAtEntity;
    private InteractionHand interactAtHand;
    private boolean interactAtSneaking;

    private boolean mainInteractSet;
    private int mainInteractEntity;
    private WrapperPlayClientInteractEntity.InteractAction mainInteractAction;
    private boolean mainInteractSneaking;
  }
}

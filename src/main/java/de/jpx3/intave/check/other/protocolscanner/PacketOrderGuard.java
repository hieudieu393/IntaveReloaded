package de.jpx3.intave.check.other.protocolscanner;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAnimation;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
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
 * Tick-scoped action ordering invariants. This intentionally overlaps combat, inventory and
 * movement checks, but stores evidence in ProtocolScanner and buffers each ordering rule
 * independently so unrelated packet oddities cannot combine into a violation.
 */
public final class PacketOrderGuard extends MetaCheckPart<ProtocolScanner, PacketOrderGuard.Meta> {
  public PacketOrderGuard(ProtocolScanner parentCheck) {
    super(parentCheck, Meta.class);
  }

  @PacketSubscription(priority = LOWEST, packetsIn = ARM_ANIMATION, ignoreCancelled = false)
  public void animation(PacketEvent event) {
    if (!(event.delegate() instanceof PacketReceiveEvent)) return;
    WrapperPlayClientAnimation animation = new WrapperPlayClientAnimation((PacketReceiveEvent) event.delegate());
    if (animation.getHand() != InteractionHand.MAIN_HAND) return;

    Meta meta = metaOf(userOf(event.getPlayer()));
    meta.animationSinceAttack = true;
    meta.attackAwaitingPostSwing = false;
    meta.animation = true;
  }

  @PacketSubscription(priority = LOWEST, packetsIn = {USE_ENTITY, ATTACK_ENTITY}, ignoreCancelled = false)
  public void interact(PacketEvent event) {
    User user = userOf(event.getPlayer());
    Meta meta = metaOf(user);
    String packetName = event.getPacketType().name();

    if ("ATTACK".equalsIgnoreCase(packetName)) {
      onAttack(user, meta);
      markActionAfterInput(user, meta, "attack");
      return;
    }
    if (!(event.delegate() instanceof PacketReceiveEvent)) return;

    WrapperPlayClientInteractEntity interaction = new WrapperPlayClientInteractEntity((PacketReceiveEvent) event.delegate());
    WrapperPlayClientInteractEntity.InteractAction action = interaction.getAction();
    if (action == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
      onAttack(user, meta);
      markActionAfterInput(user, meta, "attack");
      return;
    }

    InteractionHand hand = interaction.getHand();
    if (hand == null) hand = InteractionHand.MAIN_HAND;
    boolean sneaking = interaction.isSneaking();

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
        score(user, meta, "interact-pair",
          "INTERACT_AT did not match INTERACT entity/hand/sneak", 1.0D, 2.0D);
      } else {
        decay(meta, "interact-pair", 0.25D);
      }
      meta.pendingInteractAt = false;
    }

    // Modern right click emits MAIN_HAND then OFF_HAND for many entity interactions. Validate the
    // pair but allow a main-hand-only interaction when vanilla stops after successful consumption.
    if (hand == InteractionHand.MAIN_HAND) {
      meta.mainInteractSet = true;
      meta.mainInteractEntity = interaction.getEntityId();
      meta.mainInteractAction = action;
      meta.mainInteractSneaking = sneaking;
    } else {
      if (!meta.mainInteractSet) {
        score(user, meta, "offhand-pair", "OFF_HAND interaction without preceding MAIN_HAND", 0.75D, 2.0D);
      } else {
        boolean matches = meta.mainInteractEntity == interaction.getEntityId()
          && meta.mainInteractAction == action
          && meta.mainInteractSneaking == sneaking;
        if (!matches) {
          score(user, meta, "offhand-pair", "MAIN/OFF_HAND entity interaction mismatch", 1.0D, 2.0D);
        } else {
          decay(meta, "offhand-pair", 0.2D);
        }
      }
      meta.mainInteractSet = false;
    }

    if (meta.release || meta.dig) {
      score(user, meta, "interaction-conflict", "entity interaction after release/dig in same tick", 0.75D, 1.5D);
    }
    if (meta.useItem) {
      score(user, meta, "use-before-interact", "USE_ITEM before entity interaction in same tick", 0.75D, 1.5D);
    }
    meta.interact = true;
    markActionAfterInput(user, meta, "interact");
    markPostMovement(user, meta, "interact");
  }

  @PacketSubscription(priority = LOWEST, packetsIn = {USE_ITEM, BLOCK_PLACE, USE_ITEM_ON}, ignoreCancelled = false)
  public void use(PacketEvent event) {
    User user = userOf(event.getPlayer());
    Meta meta = metaOf(user);
    String name = event.getPacketType().name().toUpperCase(Locale.ROOT);
    boolean plainUse = "USE_ITEM".equals(name);

    if (meta.attack || meta.release || meta.dig) {
      score(user, meta, "use-conflict", "use/place after attack/release/dig in same tick", 0.75D, 1.5D);
    }
    if (!plainUse && meta.useItem) {
      score(user, meta, "use-before-place", "USE_ITEM before block interaction in same tick", 0.65D, 1.5D);
    }

    meta.use = true;
    if (plainUse) meta.useItem = true;
    else meta.place = true;
    markActionAfterInput(user, meta, plainUse ? "use-item" : "place");
    markPostMovement(user, meta, plainUse ? "use-item" : "place");
  }

  @PacketSubscription(priority = LOWEST, packetsIn = BLOCK_DIG, ignoreCancelled = false)
  public void dig(PacketEvent event) {
    if (!(event.delegate() instanceof PacketReceiveEvent)) return;
    User user = userOf(event.getPlayer());
    Meta meta = metaOf(user);
    WrapperPlayClientPlayerDigging dig = new WrapperPlayClientPlayerDigging((PacketReceiveEvent) event.delegate());
    DiggingAction action = dig.getAction();
    if (action == null) return;

    switch (action) {
      case RELEASE_USE_ITEM:
        if (meta.attack || meta.use || meta.pick || meta.dig) {
          score(user, meta, "release-conflict", "release-use-item conflicts with another action in same tick", 0.85D, 2.0D);
        }
        meta.release = true;
        break;
      case DROP_ITEM:
      case DROP_ITEM_STACK:
        if (meta.attack || meta.release || meta.use || meta.pick || meta.dig || meta.swap) {
          score(user, meta, "drop-conflict", "drop conflicts with another action in same tick", 0.85D, 2.0D);
        }
        if (meta.inventoryClick || meta.swap) {
          score(user, meta, "drop-inventory-order", "drop after inventory/swap action in same tick", 0.75D, 1.5D);
        }
        meta.drop = true;
        break;
      case SWAP_ITEM_WITH_OFFHAND:
        if (meta.attack || meta.release || meta.use || meta.pick || meta.dig || meta.drop) {
          score(user, meta, "swap-conflict", "offhand swap conflicts with another action in same tick", 0.85D, 2.0D);
        }
        meta.swap = true;
        break;
      case START_DIGGING:
      case CANCELLED_DIGGING:
      case FINISHED_DIGGING:
        if (meta.attack || meta.release || meta.use || meta.pick) {
          score(user, meta, "dig-conflict", "dig conflicts with attack/release/use/pick in same tick", 0.75D, 1.5D);
        }
        meta.dig = true;
        break;
      default:
        break;
    }

    markActionAfterInput(user, meta, "dig/" + action.name().toLowerCase(Locale.ROOT));
    markPostMovement(user, meta, "dig");
  }

  @PacketSubscription(priority = LOWEST, packetsIn = PICK_ITEM, ignoreCancelled = false)
  public void pick(PacketEvent event) {
    User user = userOf(event.getPlayer());
    Meta meta = metaOf(user);
    if (meta.attack || meta.release || meta.use || meta.dig) {
      score(user, meta, "pick-conflict", "pick-item conflicts with combat/use/dig in same tick", 0.75D, 1.5D);
    }
    meta.pick = true;
    markActionAfterInput(user, meta, "pick-item");
    markPostMovement(user, meta, "pick-item");
  }

  @PacketSubscription(priority = LOWEST, packetsIn = HELD_ITEM_SLOT_IN, ignoreCancelled = false)
  public void heldSlot(PacketEvent event) {
    User user = userOf(event.getPlayer());
    Meta meta = metaOf(user);
    if (meta.attack || meta.use || meta.release || meta.dig || meta.entityAction) {
      score(user, meta, "held-slot-order", "held-slot change conflicts with another action in same tick", 0.7D, 1.5D);
    }
    meta.heldSlot = true;
    markPostMovement(user, meta, "held-slot");
  }

  @PacketSubscription(priority = LOWEST, packetsIn = ENTITY_ACTION_IN, ignoreCancelled = false)
  public void entityAction(PacketEvent event) {
    if (!(event.delegate() instanceof PacketReceiveEvent)) return;
    User user = userOf(event.getPlayer());
    Meta meta = metaOf(user);
    WrapperPlayClientEntityAction wrapper = new WrapperPlayClientEntityAction((PacketReceiveEvent) event.delegate());
    WrapperPlayClientEntityAction.Action action = wrapper.getAction();
    if (action == null) return;

    boolean sneak = action == WrapperPlayClientEntityAction.Action.START_SNEAKING
      || action == WrapperPlayClientEntityAction.Action.STOP_SNEAKING;
    boolean sprint = action == WrapperPlayClientEntityAction.Action.START_SPRINTING
      || action == WrapperPlayClientEntityAction.Action.STOP_SPRINTING;

    if (sprint && user.protocolVersion() < ProtocolMetadata.VER_1_21_3 && meta.sneakToggle) {
      score(user, meta, "sprint-sneak-order", "sprint toggle after sneak toggle in same tick", 0.6D, 1.5D);
    }
    if (sneak && user.protocolVersion() >= ProtocolMetadata.VER_1_21_3 && meta.sprintToggle) {
      score(user, meta, "sprint-sneak-order", "sneak toggle after sprint toggle in same tick", 0.6D, 1.5D);
    }

    if (meta.attack || meta.use || meta.release || meta.dig || meta.heldSlot) {
      score(user, meta, "entity-action-order", "movement action toggle conflicts with action packet in same tick", 0.6D, 1.5D);
    }

    meta.entityAction = true;
    meta.sneakToggle |= sneak;
    meta.sprintToggle |= sprint;
    meta.inputToggleSeen = sneak || sprint;
    markPostMovement(user, meta, "entity-action");
  }

  @PacketSubscription(priority = LOWEST, packetsIn = WINDOW_CLICK, ignoreCancelled = false)
  public void windowClick(PacketEvent event) {
    if (!(event.delegate() instanceof PacketReceiveEvent)) return;
    User user = userOf(event.getPlayer());
    Meta meta = metaOf(user);
    WrapperPlayClientClickWindow click = new WrapperPlayClientClickWindow((PacketReceiveEvent) event.delegate());
    WrapperPlayClientClickWindow.WindowClickType type = click.getWindowClickType();

    if (type == WrapperPlayClientClickWindow.WindowClickType.QUICK_MOVE) {
      if (meta.pickupClick) {
        score(user, meta, "inventory-click-order", "QUICK_MOVE after PICKUP in same tick", 0.75D, 1.5D);
      }
      meta.quickMoveClick = true;
    } else if (type == WrapperPlayClientClickWindow.WindowClickType.PICKUP
      || type == WrapperPlayClientClickWindow.WindowClickType.PICKUP_ALL) {
      if (meta.quickMoveClick) {
        score(user, meta, "inventory-click-order", "PICKUP after QUICK_MOVE in same tick", 0.75D, 1.5D);
      }
      meta.pickupClick = true;
    }

    if (meta.drop) {
      score(user, meta, "inventory-drop-order", "inventory click after drop in same tick", 0.65D, 1.5D);
    }
    meta.inventoryClick = true;
    markPostMovement(user, meta, "window-click");
  }

  @PacketSubscription(priority = LOWEST, packetsIn = CLOSE_WINDOW, ignoreCancelled = false)
  public void close(PacketEvent event) {
    User user = userOf(event.getPlayer());
    Meta meta = metaOf(user);
    if (meta.drop || meta.swap) {
      score(user, meta, "window-close-order", "close-window after drop/swap in same tick", 0.6D, 1.5D);
    }
    meta.closeWindow = true;
    markPostMovement(user, meta, "close-window");
  }

  @PacketSubscription(
    priority = LOWEST,
    packetsIn = {FLYING, LOOK, POSITION, POSITION_LOOK, CLIENT_TICK_END},
    ignoreCancelled = false
  )
  public void boundary(PacketEvent event) {
    User user = userOf(event.getPlayer());
    Meta meta = metaOf(user);
    PacketType type = event.getPacketType();
    boolean hasTickEnd = user.meta().protocol().sendsClientTickEnd();

    if (hasTickEnd) {
      if (PacketTypes.isClientEndTick(type)) {
        if (meta.attackAwaitingPostSwing) {
          score(user, meta, "attack-swing-order", "attack not followed by main-hand animation before tick-end", 0.75D, 1.5D);
        }
        resetTick(meta);
      } else {
        meta.movementSeen = true;
      }
    } else {
      if (meta.attackAwaitingPostSwing) {
        score(user, meta, "attack-swing-order", "attack not followed by main-hand animation before movement boundary", 0.75D, 1.5D);
      }
      resetTick(meta);
    }
  }

  private void onAttack(User user, Meta meta) {
    if (!meta.animationSinceAttack || meta.attackAwaitingPostSwing) {
      score(user, meta, "attack-swing-order", "attack before required main-hand animation", 0.85D, 1.5D);
    }
    if (meta.use || meta.release || meta.dig || meta.pick) {
      score(user, meta, "attack-conflict", "attack conflicts with use/release/dig/pick in same tick", 0.85D, 2.0D);
    }
    meta.attack = true;
    meta.attackAwaitingPostSwing = true;
    meta.animationSinceAttack = false;
    markPostMovement(user, meta, "attack");
  }

  private void markActionAfterInput(User user, Meta meta, String action) {
    if (meta.inputToggleSeen) {
      score(user, meta, "input-action-order", action + " after sprint/sneak toggle in same tick", 0.55D, 1.5D);
    }
  }

  private void markPostMovement(User user, Meta meta, String action) {
    if (user.meta().protocol().sendsClientTickEnd() && meta.movementSeen) {
      score(user, meta, "post-movement-action", action + " after movement before CLIENT_TICK_END", 0.5D, 1.5D);
    }
  }

  private void score(User user, Meta meta, String rule, String details, double amount, double vl) {
    double factor = stable(user) ? 1.0D : 0.5D;
    double next = meta.buffers.getOrDefault(rule, 0.0D) + amount * factor;
    if (next < 2.0D) {
      meta.buffers.put(rule, next);
      return;
    }

    Violation violation = Violation.builderFor(ProtocolScanner.class)
      .forPlayer(user.player())
      .withCheckName("PacketOrder")
      .withMessage("invalid packet order")
      .withDetails(rule + ": " + details)
      .withVL(vl)
      .build();
    Modules.violationProcessor().processViolation(violation);
    meta.buffers.put(rule, 1.0D);
  }

  private static void decay(Meta meta, String rule, double amount) {
    double value = meta.buffers.getOrDefault(rule, 0.0D);
    if (value <= 0.0D) return;
    meta.buffers.put(rule, Math.max(0.0D, value - amount));
  }

  private static boolean stable(User user) {
    double average = user.meta().connection().averageMovementPacketTimestamp();
    return average > 0.0D && average <= 90.0D;
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
    meta.animation = false;
    meta.mainInteractSet = false;
    meta.pendingInteractAt = false;
    meta.movementSeen = false;
    meta.attackAwaitingPostSwing = false;
    // Allow the first attack in a fresh tick. Once an attack occurs, post-swing ordering is tracked.
    meta.animationSinceAttack = true;
    if (!meta.buffers.isEmpty()) {
      for (Map.Entry<String, Double> entry : meta.buffers.entrySet()) {
        entry.setValue(Math.max(0.0D, entry.getValue() - 0.03D));
      }
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
    private boolean animation;
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

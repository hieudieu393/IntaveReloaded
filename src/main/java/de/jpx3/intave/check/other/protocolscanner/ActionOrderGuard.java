package de.jpx3.intave.check.other.protocolscanner;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.other.ProtocolScanner;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.converter.PlayerAction;
import de.jpx3.intave.packet.reader.BlockDigReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.packet.reader.PlayerActionReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;

import java.util.Locale;

import static de.jpx3.intave.module.linker.packet.ListenerPriority.LOWEST;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

/**
 * Layered same-tick packet-order validation inspired by the PacketOrder family used by modern
 * anti-cheats. Intave's movement/combat/inventory checks remain authoritative; this class adds an
 * independent protocol-state view so a bypass has to satisfy both the gameplay model and vanilla's
 * action ordering.
 */
public final class ActionOrderGuard extends MetaCheckPart<ProtocolScanner, ActionOrderGuard.Meta> {
  public ActionOrderGuard(ProtocolScanner parentCheck) {
    super(parentCheck, Meta.class);
  }

  @PacketSubscription(
    priority = LOWEST,
    ignoreCancelled = false,
    packetsIn = {
      FLYING, LOOK, POSITION, POSITION_LOOK, CLIENT_TICK_END,
      ATTACK_ENTITY, USE_ENTITY, BLOCK_PLACE, USE_ITEM, USE_ITEM_ON,
      BLOCK_DIG, HELD_ITEM_SLOT_IN, ENTITY_ACTION_IN, WINDOW_CLICK, CLOSE_WINDOW
    }
  )
  public void receive(PacketEvent event) {
    User user = userOf(event.getPlayer());
    Meta meta = metaOf(user);
    PacketType type = event.getPacketType();

    if (isTickBoundary(type)) {
      finishTick(user, meta);
      return;
    }

    if (type == PacketType.Play.Client.ATTACK) {
      if (meta.rightClicking || meta.digging || meta.releasing || meta.dropping || meta.swapping) {
        conflict(user, meta, "attack during " + activeAction(meta), 1.0);
      }
      meta.attacking = true;
      return;
    }

    if (type == PacketType.Play.Client.USE_ENTITY) {
      meta.interacting = true;
      return;
    }

    if (isUse(type)) {
      if (meta.attacking && !meta.interacting) {
        conflict(user, meta, "use after attack without interact", 1.0);
      }
      if (meta.digging || meta.releasing || meta.dropping) {
        conflict(user, meta, "use during " + activeAction(meta), 0.8);
      }
      meta.rightClicking = true;
      return;
    }

    if (type == PacketType.Play.Client.BLOCK_DIG) {
      handleDig(event, user, meta);
      return;
    }

    if (type == PacketType.Play.Client.HELD_ITEM_SLOT) {
      if (meta.attacking || meta.rightClicking || meta.digging || meta.releasing
        || meta.dropping || meta.swapping || meta.sprintChanged || meta.sneakChanged) {
        conflict(user, meta, "slot change during " + activeAction(meta), 0.8);
      }
      meta.slotChanged = true;
      return;
    }

    if (type == PacketType.Play.Client.ENTITY_ACTION) {
      handleEntityAction(event, user, meta);
      return;
    }

    if (type == PacketType.Play.Client.WINDOW_CLICK) {
      if (meta.attacking || meta.rightClicking || meta.digging || meta.releasing || meta.dropping) {
        conflict(user, meta, "inventory click during " + activeAction(meta), 0.75);
      }
      if (meta.inventoryClosing) {
        conflict(user, meta, "inventory click after close", 1.0);
      }
      meta.inventoryClicking = true;
      return;
    }

    if (type == PacketType.Play.Client.CLOSE_WINDOW) {
      if (meta.inventoryClosing) {
        conflict(user, meta, "duplicate inventory close", 0.5);
      }
      meta.inventoryClosing = true;
    }
  }

  private void handleDig(PacketEvent event, User user, Meta meta) {
    BlockDigReader reader = PacketReaders.readerOf(event.getPacket());
    try {
      EnumWrappers.PlayerDigType action = reader.action();
      String name = action == null ? "" : action.name().toUpperCase(Locale.ROOT);

      if (name.contains("START") && name.contains("DESTROY")) {
        if (meta.rightClicking || meta.releasing || meta.attacking) {
          conflict(user, meta, "dig start during " + activeAction(meta), 0.75);
        }
        meta.digging = true;
        return;
      }

      if ((name.contains("ABORT") || name.contains("STOP")) && name.contains("DESTROY")) {
        meta.digging = false;
        return;
      }

      if (name.contains("RELEASE") && name.contains("USE")) {
        if (meta.attacking || meta.digging || meta.dropping || meta.swapping) {
          conflict(user, meta, "release-use during " + activeAction(meta), 0.8);
        }
        meta.releasing = true;
        meta.rightClicking = false;
        return;
      }

      if (name.contains("DROP")) {
        if (meta.attacking || meta.rightClicking || meta.digging || meta.releasing || meta.swapping) {
          conflict(user, meta, "drop during " + activeAction(meta), 0.8);
        }
        meta.dropping = true;
        return;
      }

      if (name.contains("SWAP")) {
        if (meta.dropping || meta.attacking || meta.rightClicking || meta.digging || meta.releasing) {
          conflict(user, meta, "offhand swap during " + activeAction(meta), 0.8);
        }
        meta.swapping = true;
      }
    } finally {
      reader.release();
    }
  }

  private void handleEntityAction(PacketEvent event, User user, Meta meta) {
    PlayerActionReader reader = PacketReaders.readerOf(event.getPacket());
    try {
      PlayerAction action = reader.playerAction();
      if (action == null) {
        return;
      }

      boolean sprint = action == PlayerAction.START_SPRINTING || action == PlayerAction.STOP_SPRINTING;
      boolean sneak = action.isSneakRelated();

      if (sprint) {
        // Vanilla changed the relative sneak/sprint input ordering in 1.21.2.
        if (user.meta().protocol().protocolVersion() < ProtocolMetadata.VER_1_21_3 && meta.sneakChanged) {
          conflict(user, meta, "sprint action after sneak action", 0.5);
        }
        meta.sprintChanged = true;
      } else if (sneak) {
        if (user.meta().protocol().protocolVersion() >= ProtocolMetadata.VER_1_21_3 && meta.sprintChanged) {
          conflict(user, meta, "sneak action after sprint action", 0.5);
        }
        meta.sneakChanged = true;
      }

      if ((sprint || sneak) && (meta.attacking || meta.rightClicking || meta.digging || meta.releasing)) {
        conflict(user, meta, (sprint ? "sprint" : "sneak") + " state change during " + activeAction(meta), 0.5);
      }
    } finally {
      reader.release();
    }
  }

  private void conflict(User user, Meta meta, String details, double weight) {
    if (hardExempt(user)) {
      return;
    }
    meta.buffer += weight;
    meta.conflicts++;
    meta.lastConflict = details;

    double threshold = parentCheck().configuration().settings().doubleBy("action-order-buffer", 2.25);
    if (meta.buffer < Math.max(1.5, threshold)) {
      return;
    }

    int vl = parentCheck().configuration().settings().intBy("action-order-vl", 3);
    Violation violation = Violation.builderFor(ProtocolScanner.class)
      .forPlayer(user.player())
      .withCheckName("BadPackets")
      .withMessage("sent conflicting action packets")
      .withDetails(details + ", conflicts=" + meta.conflicts)
      .withVL(Math.max(1, vl))
      .build();
    Modules.violationProcessor().processViolation(violation);
    meta.buffer = Math.max(0.75, meta.buffer - 1.25);
    meta.conflicts = 0;
  }

  private static void finishTick(User user, Meta meta) {
    if (!hardExempt(user) && meta.conflicts == 0) {
      meta.buffer = Math.max(0.0, meta.buffer - 0.35);
    }
    meta.resetTick();
  }

  private static boolean hardExempt(User user) {
    return user.meta().movement().awaitTeleport
      || user.meta().movement().expectTeleport
      || user.meta().movement().inRespawnScreen
      || user.meta().movement().isInVehicle();
  }

  private static boolean isUse(PacketType type) {
    return type == PacketType.Play.Client.BLOCK_PLACE
      || "USE_ITEM".equalsIgnoreCase(type.name())
      || "USE_ITEM_ON".equalsIgnoreCase(type.name());
  }

  private static boolean isTickBoundary(PacketType type) {
    return type == PacketType.Play.Client.FLYING
      || type == PacketType.Play.Client.LOOK
      || type == PacketType.Play.Client.POSITION
      || type == PacketType.Play.Client.POSITION_LOOK
      || "CLIENT_TICK_END".equalsIgnoreCase(type.name());
  }

  private static String activeAction(Meta meta) {
    if (meta.attacking) return "attack";
    if (meta.rightClicking) return "use-item";
    if (meta.digging) return "digging";
    if (meta.releasing) return "release-use";
    if (meta.dropping) return "drop";
    if (meta.swapping) return "offhand-swap";
    if (meta.inventoryClicking) return "inventory-click";
    if (meta.sprintChanged) return "sprint-change";
    if (meta.sneakChanged) return "sneak-change";
    return "another action";
  }

  public static final class Meta extends CheckCustomMetadata {
    private boolean attacking;
    private boolean interacting;
    private boolean rightClicking;
    private boolean digging;
    private boolean releasing;
    private boolean dropping;
    private boolean swapping;
    private boolean slotChanged;
    private boolean sprintChanged;
    private boolean sneakChanged;
    private boolean inventoryClicking;
    private boolean inventoryClosing;
    private double buffer;
    private int conflicts;
    private String lastConflict;

    private void resetTick() {
      attacking = false;
      interacting = false;
      rightClicking = false;
      digging = false;
      releasing = false;
      dropping = false;
      swapping = false;
      slotChanged = false;
      sprintChanged = false;
      sneakChanged = false;
      inventoryClicking = false;
      inventoryClosing = false;
      conflicts = 0;
      lastConflict = null;
    }
  }
}

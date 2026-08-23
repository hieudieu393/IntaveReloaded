package de.jpx3.intave.check.other.protocolscanner;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.other.ProtocolScanner;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.converter.PlayerAction;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.packet.reader.PlayerActionReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;

import static de.jpx3.intave.module.linker.packet.ListenerPriority.LOWEST;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class PostPacketOrder extends MetaCheckPart<ProtocolScanner, PostPacketOrder.Meta> {
  public PostPacketOrder(ProtocolScanner parentCheck) {
    super(parentCheck, Meta.class);
  }

  @PacketSubscription(
    priority = LOWEST,
    ignoreCancelled = false,
    packetsIn = {
      FLYING, LOOK, POSITION, POSITION_LOOK, CLIENT_TICK_END,
      TRANSACTION, PONG,
      ABILITIES_IN, HELD_ITEM_SLOT_IN, ATTACK_ENTITY, USE_ENTITY,
      BLOCK_PLACE, USE_ITEM, USE_ITEM_ON, BLOCK_DIG, SPECTATE, ENTITY_ACTION_IN
    }
  )
  public void receive(ProtocolPacketEvent event) {
    User user = userOf(event.getPlayer());
    Meta meta = metaOf(user);
    PacketTypeCommon type = event.getPacketType();

    if (isMovement(type)) {
      meta.sentMovement = true;
      meta.firstPostAction = null;
      meta.postActions = 0;
      return;
    }
    if (type == PacketType.Play.Client.CLIENT_TICK_END) {
      meta.sentMovement = false;
      meta.firstPostAction = null;
      meta.postActions = 0;
      decay(meta, 0.15);
      return;
    }
    if (isFeedbackBoundary(type)) {
      if (meta.sentMovement && meta.postActions > 0 && tickingReliably(user)) {
        meta.buffer += Math.min(1.5, 0.5 + meta.postActions * 0.25);
        if (meta.buffer > 2.5) {
          int vl = parentCheck().configuration().settings().intBy("packet-order-vl", 5);
          String packet = meta.firstPostAction == null ? "unknown" : meta.firstPostAction;
          Violation violation = Violation.builderFor(ProtocolScanner.class)
            .forPlayer(event.getPlayer())
            .withMessage("sent packets in an invalid tick order")
            .withDetails(packet + " after movement, actions=" + meta.postActions)
            .withVL(Math.max(1, vl))
            .build();
          Modules.violationProcessor().processViolation(violation);
          meta.buffer = Math.max(1.25, meta.buffer - 1.0);
        }
      } else {
        decay(meta, 0.25);
      }
      meta.sentMovement = false;
      meta.firstPostAction = null;
      meta.postActions = 0;
      return;
    }

    if (meta.sentMovement && isTrackedAction(type)) {
      if (isExemptEntityAction(user, event)) return;
      if (meta.firstPostAction == null) meta.firstPostAction = packetLabel(type);
      meta.postActions++;
    }
  }

  private static boolean isExemptEntityAction(User user, ProtocolPacketEvent event) {
    if (event.getPacketType() != PacketType.Play.Client.ENTITY_ACTION) return false;
    if (user.meta().movement().isInVehicle()) return true;
    PlayerActionReader reader = PacketReaders.readerOf(event);
    try {
      return reader.playerAction() == PlayerAction.STOP_SLEEPING;
    } finally {
      reader.release();
    }
  }

  private static boolean tickingReliably(User user) {
    double average = user.meta().connection().averageMovementPacketTimestamp();
    long sinceMovement = System.currentTimeMillis() - user.meta().connection().lastMovementPacket();
    return average > 0 && average <= 90 && sinceMovement <= 250;
  }

  private static boolean isMovement(PacketTypeCommon type) {
    return type == PacketType.Play.Client.PLAYER_FLYING
      || type == PacketType.Play.Client.PLAYER_ROTATION
      || type == PacketType.Play.Client.PLAYER_POSITION
      || type == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION;
  }

  private static boolean isFeedbackBoundary(PacketTypeCommon type) {
    return type == PacketType.Play.Client.PONG || type == PacketType.Play.Client.WINDOW_CONFIRMATION;
  }

  private static boolean isTrackedAction(PacketTypeCommon type) {
    return type == PacketType.Play.Client.PLAYER_ABILITIES
      || type == PacketType.Play.Client.HELD_ITEM_CHANGE
      || type == PacketType.Play.Client.INTERACT_ENTITY
      || type == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT
      || type == PacketType.Play.Client.USE_ITEM
      || type == PacketType.Play.Client.PLAYER_DIGGING
      || type == PacketType.Play.Client.SPECTATE
      || type == PacketType.Play.Client.ENTITY_ACTION;
  }

  private static String packetLabel(PacketTypeCommon type) {
    if (type == PacketType.Play.Client.PLAYER_ABILITIES) return "abilities";
    if (type == PacketType.Play.Client.HELD_ITEM_CHANGE) return "held item slot";
    if (type == PacketType.Play.Client.INTERACT_ENTITY) return "use entity";
    if (type == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) return "block place";
    if (type == PacketType.Play.Client.USE_ITEM) return "use item";
    if (type == PacketType.Play.Client.PLAYER_DIGGING) return "block dig";
    if (type == PacketType.Play.Client.SPECTATE) return "spectate";
    if (type == PacketType.Play.Client.ENTITY_ACTION) return "entity action";
    return "unknown";
  }

  private static void decay(Meta meta, double amount) {
    meta.buffer = Math.max(0.0, meta.buffer - amount);
  }

  public static final class Meta extends CheckCustomMetadata {
    private boolean sentMovement;
    private int postActions;
    private String firstPostAction;
    private double buffer;
  }
}

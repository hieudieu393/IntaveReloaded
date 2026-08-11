package de.jpx3.intave.check.other.protocolscanner;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientSettings;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.other.ProtocolScanner;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketId;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.module.tracker.entity.EntityTracker;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.ENTITY_ACTION_IN;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.SETTINGS;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.USE_ENTITY;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.WINDOW_CLICK;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.OPEN_WINDOW;

/**
 * ProtocolPacketEvents-native guards for protocol fields that Intave's ProtocolLib-shaped reader facade
 * historically did not expose. Keeping them in ProtocolScanner lets us add modern packet coverage
 * without hard-coded field indices or weakening the existing reader compatibility layer.
 */
public final class ExtendedProtocolGuards extends MetaCheckPart<ProtocolScanner, ExtendedProtocolGuards.Meta> {
  private static final int LECTERN_MENU_TYPE = 17;
  private static final double INTERACT_VECTOR_TOLERANCE = 0.05D;

  public ExtendedProtocolGuards(ProtocolScanner parentCheck) {
    super(parentCheck, Meta.class);
  }

  @PacketSubscription(packetsIn = SETTINGS, ignoreCancelled = false)
  public void receiveSettings(ProtocolPacketEvent event) {
    if (!(event instanceof PacketReceiveEvent)) {
      return;
    }
    WrapperPlayClientSettings wrapper = new WrapperPlayClientSettings((PacketReceiveEvent) event);
    int distance = wrapper.getViewDistance();
    if (distance >= 2) {
      return;
    }

    wrapper.setViewDistance(2);
    flag(userOf(event.getPlayer()), "client-settings", "view-distance=" + distance, 5.0);
  }

  @PacketSubscription(packetsOut = OPEN_WINDOW, ignoreCancelled = false)
  public void receiveOpenWindow(ProtocolPacketEvent event) {
    if (!(event.delegate() instanceof PacketSendEvent)) {
      return;
    }
    User user = userOf(event.getPlayer());
    WrapperPlayServerOpenWindow wrapper = new WrapperPlayServerOpenWindow((PacketSendEvent) event.delegate());
    Meta meta = metaOf(user);
    meta.lecternWindowId = wrapper.getType() == LECTERN_MENU_TYPE ? wrapper.getContainerId() : -1;
  }

  @PacketSubscription(packetsOut = PacketId.Server.CLOSE_WINDOW, ignoreCancelled = false)
  public void receiveServerClose(ProtocolPacketEvent event) {
    metaOf(userOf(event.getPlayer())).lecternWindowId = -1;
  }

  @PacketSubscription(packetsIn = PacketId.Client.CLOSE_WINDOW, ignoreCancelled = false)
  public void receiveClientClose(ProtocolPacketEvent event) {
    metaOf(userOf(event.getPlayer())).lecternWindowId = -1;
  }

  @PacketSubscription(packetsIn = WINDOW_CLICK, ignoreCancelled = false)
  public void receiveWindowClick(ProtocolPacketEvent event) {
    if (!(event instanceof PacketReceiveEvent)) {
      return;
    }
    User user = userOf(event.getPlayer());
    Meta meta = metaOf(user);
    if (meta.lecternWindowId <= 0) {
      return;
    }

    WrapperPlayClientClickWindow wrapper = new WrapperPlayClientClickWindow((PacketReceiveEvent) event);
    if (wrapper.getWindowId() != meta.lecternWindowId) {
      return;
    }

    event.setCancelled(true);
    flag(user, "lectern-click",
      "window=" + wrapper.getWindowId() + ", button=" + wrapper.getButton()
        + ", type=" + wrapper.getWindowClickType(), 20.0);
  }

  @PacketSubscription(packetsIn = ENTITY_ACTION_IN, ignoreCancelled = false)
  public void receiveEntityAction(ProtocolPacketEvent event) {
    if (!(event instanceof PacketReceiveEvent)) {
      return;
    }
    WrapperPlayClientEntityAction wrapper = new WrapperPlayClientEntityAction((PacketReceiveEvent) event);
    int boost = wrapper.getJumpBoost();
    WrapperPlayClientEntityAction.Action action = wrapper.getAction();
    int entityId = wrapper.getEntityId();

    boolean invalid = Math.abs(boost) > 100
      || entityId != event.getPlayer().getEntityId()
      || (action != WrapperPlayClientEntityAction.Action.START_JUMPING_WITH_HORSE && boost != 0);
    if (!invalid) {
      return;
    }

    event.setCancelled(true);
    flag(userOf(event.getPlayer()), "entity-action",
      "action=" + action + ", boost=" + boost + ", entity=" + entityId, 10.0);
  }

  @PacketSubscription(packetsIn = USE_ENTITY, ignoreCancelled = false)
  public void receiveInteractEntity(ProtocolPacketEvent event) {
    if (!(event instanceof PacketReceiveEvent)) {
      return;
    }
    WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity((PacketReceiveEvent) event);
    if (wrapper.getAction() != WrapperPlayClientInteractEntity.InteractAction.INTERACT_AT) {
      return;
    }

    Vector3d vector = wrapper.getLocation();
    if (vector == null) {
      return;
    }

    User user = userOf(event.getPlayer());
    Entity target = EntityTracker.entityByIdentifier(user, wrapper.getEntityId());
    if (target == null || !target.isPlayer || target.boundingBox() == null) {
      return;
    }

    BoundingBox box = target.boundingBox();
    double halfX = (box.maxX - box.minX) * 0.5D + INTERACT_VECTOR_TOLERANCE;
    double halfZ = (box.maxZ - box.minZ) * 0.5D + INTERACT_VECTOR_TOLERANCE;
    double height = (box.maxY - box.minY) + INTERACT_VECTOR_TOLERANCE;
    boolean valid = vector.x >= -halfX && vector.x <= halfX
      && vector.z >= -halfZ && vector.z <= halfZ
      && vector.y >= -INTERACT_VECTOR_TOLERANCE && vector.y <= height;
    if (valid) {
      return;
    }

    flag(user, "interact-vector",
      "target=" + wrapper.getEntityId() + ", vector=" + format(vector.x) + "/"
        + format(vector.y) + "/" + format(vector.z) + ", hitbox="
        + format(halfX * 2.0D) + "x" + format(height) + "x" + format(halfZ * 2.0D), 10.0);
  }

  private void flag(User user, String rule, String details, double vl) {
    Violation violation = Violation.builderFor(ProtocolScanner.class)
      .forPlayer(user.player())
      .withCheckName("BadPackets")
      .withMessage("failed " + rule)
      .withDetails(details)
      .withVL(vl)
      .build();
    Modules.violationProcessor().processViolation(violation);
  }

  private static String format(double value) {
    return String.format(java.util.Locale.ROOT, "%.4f", value);
  }

  public static final class Meta extends CheckCustomMetadata {
    private int lecternWindowId = -1;
  }
}

package de.jpx3.intave.check.combat.heuristics.modern;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientUseItem;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;

import static de.jpx3.intave.module.linker.packet.ListenerPriority.LOW;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

/**
 * Minecraft 1.21+ embeds yaw/pitch in USE_ITEM. Vanilla keeps those rotations consistent with the
 * movement tick rotation. This adds an independent Aim signal without replacing Intave's existing
 * sensitivity/snap/accuracy heuristics.
 */
public final class UseItemRotationHeuristic extends ModernCombatHeuristic<UseItemRotationHeuristic.Meta> {
  public UseItemRotationHeuristic(Heuristics parentCheck) {
    super(parentCheck, Meta.class);
  }

  @PacketSubscription(priority = LOW, packetsIn = USE_ITEM, ignoreCancelled = false)
  public void receiveUseItem(ProtocolPacketEvent event) {
    User user = userOf(event.getPlayer());
    if (!applicable(user) || !(event.delegate() instanceof PacketReceiveEvent)) {
      reset(metaOf(user));
      return;
    }

    WrapperPlayClientUseItem wrapper = new WrapperPlayClientUseItem((PacketReceiveEvent) event.delegate());
    float yaw = wrapper.getYaw();
    float pitch = wrapper.getPitch();
    Meta meta = metaOf(user);

    if (meta.pending > 0 && (different(meta.yaw, yaw) || different(meta.pitch, pitch))) {
      bufferFlag(user, meta,
        "multiple use-item rotations before tick old=" + fmt(meta.yaw) + "/" + fmt(meta.pitch)
          + " new=" + fmt(yaw) + "/" + fmt(pitch));
    }

    meta.yaw = yaw;
    meta.pitch = pitch;
    if (meta.pending < Integer.MAX_VALUE) {
      meta.pending++;
    }
  }

  @PacketSubscription(
    priority = LOW,
    packetsIn = {FLYING, LOOK, POSITION, POSITION_LOOK, CLIENT_TICK_END},
    ignoreCancelled = false
  )
  public void receiveTick(ProtocolPacketEvent event) {
    User user = userOf(event.getPlayer());
    Meta meta = metaOf(user);
    if (meta.pending <= 0) {
      return;
    }
    if (!applicable(user)) {
      reset(meta);
      return;
    }

    MovementMetadata movement = user.meta().movement();
    boolean currentMatch = !different(meta.yaw, movement.rotationYaw)
      && !different(meta.pitch, movement.rotationPitch);
    boolean previousMatch = !different(meta.yaw, movement.lastRotationYaw)
      && !different(meta.pitch, movement.lastRotationPitch);

    // ViaVersion/tick skipping can expose the previous tick's rotation. Accept either, and only
    // score repeated mismatches while packet cadence is healthy.
    if (!currentMatch && !previousMatch && tickingReliably(user)) {
      bufferFlag(user, meta,
        "use-item=" + fmt(meta.yaw) + "/" + fmt(meta.pitch)
          + " tick=" + fmt(movement.rotationYaw) + "/" + fmt(movement.rotationPitch)
          + " last=" + fmt(movement.lastRotationYaw) + "/" + fmt(movement.lastRotationPitch));
    } else {
      meta.buffer = Math.max(0.0, meta.buffer - 0.2);
    }
    meta.pending = 0;
  }

  private void bufferFlag(User user, Meta meta, String details) {
    meta.buffer += 1.0;
    if (meta.buffer < 2.0) {
      return;
    }
    flag(user, "aim-use-item-rotation", details, 3.0);
    meta.buffer = 1.0;
  }

  private static boolean applicable(User user) {
    return user.protocolVersion() >= ProtocolMetadata.VER_1_21
      && !user.meta().abilities().hasViewEntity;
  }

  private static boolean tickingReliably(User user) {
    double average = user.meta().connection().averageMovementPacketTimestamp();
    return average > 0.0 && average <= 90.0;
  }

  private static boolean different(float a, float b) {
    return Float.floatToIntBits(a) != Float.floatToIntBits(b);
  }

  private static String fmt(float value) {
    return String.format(java.util.Locale.ROOT, "%.3f", value);
  }

  private static void reset(Meta meta) {
    meta.pending = 0;
    meta.buffer = Math.max(0.0, meta.buffer - 0.25);
  }

  public static final class Meta extends CheckCustomMetadata {
    private float yaw;
    private float pitch;
    private int pending;
    private double buffer;
  }
}

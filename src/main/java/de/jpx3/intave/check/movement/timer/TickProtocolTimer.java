package de.jpx3.intave.check.movement.timer;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.movement.Timer;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.PacketTypes;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;

import static de.jpx3.intave.module.linker.packet.ListenerPriority.LOWEST;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

/** Extra packet-clock invariants layered on Timer's transaction-synchronized PlayerTime. */
public final class TickProtocolTimer extends MetaCheckPart<Timer, TickProtocolTimer.Meta> {
  private static final long VEHICLE_DRIFT_NS = 150_000_000L;
  private static final long NEGATIVE_DRIFT_NS = 1_200_000_000L;
  private static final long NEGATIVE_IDLE_RESET_NS = 250_000_000L;
  private static final long CLIENT_TICK_NS = 50_000_000L;

  public TickProtocolTimer(Timer parentCheck) {
    super(parentCheck, Meta.class);
  }

  @PacketSubscription(
    priority = LOWEST,
    packetsIn = {FLYING, LOOK, POSITION, POSITION_LOOK, CLIENT_TICK_END},
    ignoreCancelled = false
  )
  public void playerTick(PacketEvent event) {
    User user = userOf(event.getPlayer());
    Meta meta = metaOf(user);
    PacketType type = event.getPacketType();
    boolean positionBearing = isPositionBearing(type);

    // NegativeTimer cannot safely judge an idle 1.9+ client. Only carry the slow-clock balance
    // across consecutive position-bearing ticks, and reset it on long silence/state uncertainty.
    if (positionBearing) {
      meta.positionSeenThisTick = true;
      if (!user.meta().protocol().sendsClientTickEnd()) {
        sampleNegativeClock(user, meta);
      }
    }

    if (!user.meta().protocol().sendsClientTickEnd()) return;

    if (PacketTypes.isClientEndTick(type)) {
      if (meta.flyingPackets > 1 && stable(user)) {
        meta.tickBuffer += Math.min(1.5D, 0.5D + 0.5D * (meta.flyingPackets - 1));
        maybeFlag(user, meta, "multiple movement packets before tick-end: " + meta.flyingPackets);
      } else {
        meta.tickBuffer = Math.max(0.0D, meta.tickBuffer - 0.2D);
      }
      if (meta.positionSeenThisTick) {
        sampleNegativeClock(user, meta);
      }
      meta.positionSeenThisTick = false;
      meta.receivedTickEnd = true;
      meta.flyingPackets = 0;
      return;
    }

    MovementMetadata movement = user.meta().movement();
    if (movement.awaitTeleport || movement.expectTeleport) {
      meta.receivedTickEnd = true;
      meta.flyingPackets = 0;
      resetNegative(meta);
      return;
    }

    if (!meta.receivedTickEnd && meta.flyingPackets > 0 && stable(user)) {
      meta.tickBuffer += 0.75D;
      maybeFlag(user, meta, "movement arrived before required client tick-end");
    }
    meta.receivedTickEnd = false;
    meta.flyingPackets++;
  }

  @PacketSubscription(
    priority = LOWEST,
    packetsIn = {VEHICLE_MOVE, STEER_VEHICLE},
    ignoreCancelled = false
  )
  public void vehicleTick(PacketEvent event) {
    User user = userOf(event.getPlayer());
    MovementMetadata movement = user.meta().movement();
    Meta meta = metaOf(user);
    if (!movement.isInVehicle() || movement.awaitTeleport || movement.expectTeleport) {
      meta.vehicleClock = 0L;
      meta.vehicleDummy = false;
      return;
    }

    String name = event.getPacketType().name();
    boolean count;
    if ("VEHICLE_MOVE".equalsIgnoreCase(name)) {
      meta.vehicleDummy = false;
      count = true;
    } else {
      // Match vanilla's steer/vehicle alternation: only count consecutive steer packets when the
      // server controls the vehicle, otherwise the following VEHICLE_MOVE owns the tick.
      count = meta.vehicleDummy;
      meta.vehicleDummy = true;
    }
    if (!count) return;

    long now = System.nanoTime();
    if (meta.vehicleClock == 0L) {
      meta.vehicleClock = now;
      return;
    }
    meta.vehicleClock += CLIENT_TICK_NS;
    if (meta.vehicleClock > now + VEHICLE_DRIFT_NS) {
      meta.vehicleBuffer += 1.0D;
      if (meta.vehicleBuffer >= 2.0D) {
        flag(user, "vehicle packets are running ahead of the client clock",
          "ahead=" + ((meta.vehicleClock - now) / 1_000_000L) + "ms", 2.0D);
        meta.vehicleBuffer = 1.0D;
      }
      meta.vehicleClock -= CLIENT_TICK_NS;
    } else {
      meta.vehicleBuffer = Math.max(0.0D, meta.vehicleBuffer - 0.1D);
      // Limit the amount of lag credit a high-ping/paused client can bank.
      meta.vehicleClock = Math.max(meta.vehicleClock, now - 1_000_000_000L);
    }
  }

  private void sampleNegativeClock(User user, Meta meta) {
    MovementMetadata movement = user.meta().movement();
    long now = System.nanoTime();
    if (movement.awaitTeleport || movement.expectTeleport || movement.isInVehicle()
      || user.meta().abilities().ignoringMovementPackets() || !stable(user)) {
      resetNegative(meta);
      return;
    }

    if (meta.lastNegativeSample == 0L || now - meta.lastNegativeSample > NEGATIVE_IDLE_RESET_NS) {
      meta.negativeClock = now;
      meta.lastNegativeSample = now;
      meta.negativeBuffer = Math.max(0.0D, meta.negativeBuffer - 0.25D);
      return;
    }

    meta.lastNegativeSample = now;
    meta.negativeClock += CLIENT_TICK_NS;
    long behind = now - meta.negativeClock;
    if (behind > NEGATIVE_DRIFT_NS) {
      meta.negativeBuffer += 1.0D;
      if (meta.negativeBuffer >= 2.0D) {
        flag(user, "movement packets are running behind the client clock",
          "behind=" + (behind / 1_000_000L) + "ms", 1.5D);
        meta.negativeBuffer = 1.0D;
      }
      // Mirror the source check's gradual recovery instead of snapping the clock to real time.
      meta.negativeClock += CLIENT_TICK_NS;
    } else {
      meta.negativeBuffer = Math.max(0.0D, meta.negativeBuffer - 0.10D);
    }
  }

  private static boolean isPositionBearing(PacketType type) {
    return type == PacketType.Play.Client.POSITION || type == PacketType.Play.Client.POSITION_LOOK;
  }

  private static void resetNegative(Meta meta) {
    meta.negativeClock = 0L;
    meta.lastNegativeSample = 0L;
    meta.positionSeenThisTick = false;
    meta.negativeBuffer = Math.max(0.0D, meta.negativeBuffer - 0.5D);
  }

  private void maybeFlag(User user, Meta meta, String details) {
    if (meta.tickBuffer < 2.0D) return;
    flag(user, "invalid client tick-end timing", details, 2.0D);
    meta.tickBuffer = 1.0D;
  }

  private void flag(User user, String message, String details, double vl) {
    Violation violation = Violation.builderFor(Timer.class)
      .forPlayer(user.player())
      .withCheckName("Timer")
      .withMessage(message)
      .withDetails(details)
      .withVL(vl)
      .build();
    Modules.violationProcessor().processViolation(violation);
  }

  private static boolean stable(User user) {
    double average = user.meta().connection().averageMovementPacketTimestamp();
    return average > 0.0D && average <= 90.0D;
  }

  public static final class Meta extends CheckCustomMetadata {
    private boolean receivedTickEnd = true;
    private int flyingPackets;
    private double tickBuffer;
    private boolean vehicleDummy;
    private long vehicleClock;
    private double vehicleBuffer;
    private boolean positionSeenThisTick;
    private long negativeClock;
    private long lastNegativeSample;
    private double negativeBuffer;
  }
}
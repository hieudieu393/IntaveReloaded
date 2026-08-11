package de.jpx3.intave.check.movement.physics;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientSteerVehicle;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientVehicleMove;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.check.MetaCheck;
import de.jpx3.intave.check.movement.Physics;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.PacketTypes;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;

import static de.jpx3.intave.module.linker.packet.ListenerPriority.LOWEST;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

/** Layered vehicle protocol/state invariants; Physics remains the authoritative motion simulator. */
public final class VehicleSignalGuard extends MetaCheck<VehicleSignalGuard.Meta> {
  public VehicleSignalGuard() {
    super("VehicleSignalGuard", "vehiclesignalguard", Meta.class);
  }

  @Override
  public boolean enabled() { return true; }

  @Override
  public boolean performLinkage() { return true; }

  @PacketSubscription(priority = LOWEST, packetsIn = STEER_VEHICLE, ignoreCancelled = false)
  public void steer(ProtocolPacketEvent event) {
    if (!(event.delegate() instanceof PacketReceiveEvent)) return;
    User user = userOf(event.getPlayer());
    MovementMetadata movement = user.meta().movement();
    Meta meta = metaOf(user);
    WrapperPlayClientSteerVehicle steer = new WrapperPlayClientSteerVehicle((PacketReceiveEvent) event.delegate());

    float sideways = steer.getSideways();
    float forward = steer.getForward();
    if (!Float.isFinite(sideways) || !Float.isFinite(forward)
      || Math.abs(sideways) > 1.0001F || Math.abs(forward) > 1.0001F) {
      event.setCancelled(true);
      flag(user, "invalid vehicle input", "sideways=" + sideways + ", forward=" + forward, 8.0D);
      return;
    }

    if (!movement.isInVehicle() && !movement.awaitTeleport && !movement.expectTeleport) {
      meta.stateBuffer += 0.75D;
      maybeStateFlag(user, meta, "STEER_VEHICLE while not riding");
    } else {
      meta.stateBuffer = Math.max(0.0D, meta.stateBuffer - 0.2D);
    }

    meta.steersThisTick++;
    if (meta.steersThisTick > 2 && stable(user)) {
      meta.multiBuffer += 0.5D;
      if (meta.multiBuffer >= 2.0D) {
        flag(user, "duplicate vehicle input", "steer packets=" + meta.steersThisTick + " in one client tick", 2.0D);
        meta.multiBuffer = 1.0D;
      }
    }
  }

  @PacketSubscription(priority = LOWEST, packetsIn = VEHICLE_MOVE, ignoreCancelled = false)
  public void move(ProtocolPacketEvent event) {
    if (!(event.delegate() instanceof PacketReceiveEvent)) return;
    User user = userOf(event.getPlayer());
    MovementMetadata movement = user.meta().movement();
    Meta meta = metaOf(user);
    WrapperPlayClientVehicleMove move = new WrapperPlayClientVehicleMove((PacketReceiveEvent) event.delegate());
    Vector3d position = move.getPosition();

    if (position == null || !Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)
      || !Float.isFinite(move.getYaw()) || !Float.isFinite(move.getPitch())) {
      event.setCancelled(true);
      flag(user, "invalid vehicle movement", "non-finite vehicle position/rotation", 10.0D);
      return;
    }

    if (!movement.isInVehicle() && !movement.awaitTeleport && !movement.expectTeleport) {
      meta.stateBuffer += 1.0D;
      maybeStateFlag(user, meta, "VEHICLE_MOVE while not riding");
    } else {
      meta.stateBuffer = Math.max(0.0D, meta.stateBuffer - 0.25D);
    }

    meta.movesThisTick++;
    if (meta.movesThisTick > 1 && stable(user)) {
      meta.multiBuffer += 1.0D;
      if (meta.multiBuffer >= 2.0D) {
        flag(user, "duplicate vehicle movement", "vehicle moves=" + meta.movesThisTick + " in one client tick", 2.0D);
        meta.multiBuffer = 1.0D;
      }
    }
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
    meta.movesThisTick = 0;
    meta.steersThisTick = 0;
    meta.multiBuffer = Math.max(0.0D, meta.multiBuffer - 0.05D);
  }

  private void maybeStateFlag(User user, Meta meta, String details) {
    if (meta.stateBuffer < 2.0D) return;
    flag(user, "vehicle state mismatch", details, 3.0D);
    meta.stateBuffer = 1.0D;
  }

  private void flag(User user, String message, String details, double vl) {
    Violation violation = Violation.builderFor(Physics.class)
      .forPlayer(user.player())
      .withCheckName("Vehicle")
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
    private int movesThisTick;
    private int steersThisTick;
    private double stateBuffer;
    private double multiBuffer;
  }
}

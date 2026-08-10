package de.jpx3.intave.check.world.breakspeedlimiter;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.world.BreakSpeedLimiter;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.PacketTypes;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;

import static de.jpx3.intave.module.linker.packet.ListenerPriority.LOWEST;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

/**
 * Layered breaking checks inspired by the protocol invariants used by modern prediction anti-cheats.
 * It complements InteractionRaytrace and duration checks with raw face validation, break-state
 * continuity, same-tick multi-break detection and conservative face/eye geometry.
 */
public final class BreakProtocolGuard extends MetaCheckPart<BreakSpeedLimiter, BreakProtocolGuard.Meta> {
  private static final double GEOMETRY_TOLERANCE = 0.35D;
  private static final double[] COMMON_EYE_HEIGHTS = {1.62D, 1.27D, 0.40D};

  public BreakProtocolGuard(BreakSpeedLimiter parentCheck) {
    super(parentCheck, Meta.class);
  }

  @PacketSubscription(priority = LOWEST, packetsIn = BLOCK_DIG, ignoreCancelled = false)
  public void dig(PacketEvent event) {
    if (!(event.delegate() instanceof PacketReceiveEvent)) {
      return;
    }

    User user = userOf(event.getPlayer());
    WrapperPlayClientPlayerDigging dig = new WrapperPlayClientPlayerDigging((PacketReceiveEvent) event.delegate());
    DiggingAction action = dig.getAction();
    Vector3i pos = dig.getBlockPosition();
    int face = dig.getBlockFaceId();
    if (action == null || pos == null) {
      return;
    }

    Meta meta = metaOf(user);
    if (face < 0 || face > 5) {
      event.setCancelled(true);
      flag(user, "invalid break face", "face=" + face + ", action=" + action, 10.0D);
      return;
    }

    if (!isMiningAction(action)) {
      return;
    }

    checkMultiBreak(user, meta, action, pos, face);
    checkState(user, meta, action, pos, face);
    if (action != DiggingAction.CANCELLED_DIGGING) {
      checkFaceGeometry(user, meta, pos, face);
    } else if (face != 0) {
      meta.cancelFaceBuffer += 1.0D;
      if (meta.cancelFaceBuffer >= 2.0D) {
        flag(user, "invalid cancel face", "face=" + face + ", pos=" + compact(pos), 3.0D);
        meta.cancelFaceBuffer = 1.0D;
      }
    } else {
      meta.cancelFaceBuffer = Math.max(0.0D, meta.cancelFaceBuffer - 0.25D);
    }
  }

  @PacketSubscription(
    priority = LOWEST,
    packetsIn = {FLYING, LOOK, POSITION, POSITION_LOOK, CLIENT_TICK_END},
    ignoreCancelled = false
  )
  public void tick(PacketEvent event) {
    User user = userOf(event.getPlayer());
    PacketType type = event.getPacketType();
    boolean modernBoundary = user.meta().protocol().sendsClientTickEnd();
    boolean boundary = modernBoundary ? PacketTypes.isClientEndTick(type) : !PacketTypes.isClientEndTick(type);
    if (!boundary) {
      return;
    }

    Meta meta = metaOf(user);
    meta.hasBreakThisTick = false;
    meta.lastTickPos = null;
    meta.lastTickFace = -1;
    meta.multiBuffer = Math.max(0.0D, meta.multiBuffer - 0.05D);
  }

  private void checkState(User user, Meta meta, DiggingAction action, Vector3i pos, int face) {
    if (action == DiggingAction.START_DIGGING) {
      meta.activePos = pos;
      meta.activeFace = face;
      // Mojang can cancel a block and resume it without another START after looking away. Keep the
      // cancelled position separately so FINISHED_DIGGING can match either legitimate state.
      return;
    }

    if (action == DiggingAction.CANCELLED_DIGGING) {
      if (meta.activePos != null && !same(meta.activePos, pos)) {
        meta.stateBuffer += 0.75D;
        maybeFlagState(user, meta, "cancelled different block active=" + compact(meta.activePos) + ", got=" + compact(pos));
      } else {
        meta.stateBuffer = Math.max(0.0D, meta.stateBuffer - 0.15D);
      }
      meta.cancelledPos = pos;
      meta.activePos = null;
      meta.activeFace = -1;
      return;
    }

    if (action == DiggingAction.FINISHED_DIGGING) {
      boolean matchesActive = meta.activePos != null && same(meta.activePos, pos);
      boolean matchesCancelled = meta.cancelledPos != null && same(meta.cancelledPos, pos);
      if (!matchesActive && !matchesCancelled) {
        meta.stateBuffer += 1.0D;
        maybeFlagState(user, meta,
          "finished untracked block active=" + compact(meta.activePos)
            + ", cancelled=" + compact(meta.cancelledPos) + ", got=" + compact(pos));
      } else {
        meta.stateBuffer = Math.max(0.0D, meta.stateBuffer - 0.25D);
      }
      meta.activePos = null;
      meta.cancelledPos = null;
      meta.activeFace = -1;
    }
  }

  private void maybeFlagState(User user, Meta meta, String details) {
    if (meta.stateBuffer < 2.0D) {
      return;
    }
    flag(user, "invalid break sequence", details, 3.0D);
    meta.stateBuffer = 1.0D;
  }

  private void checkMultiBreak(User user, Meta meta, DiggingAction action, Vector3i pos, int face) {
    if (action == DiggingAction.CANCELLED_DIGGING) {
      return;
    }

    if (meta.hasBreakThisTick && (!same(meta.lastTickPos, pos) || meta.lastTickFace != face)) {
      if (tickingReliably(user)) {
        meta.multiBuffer += 1.0D;
        if (meta.multiBuffer >= 2.0D) {
          flag(user, "multiple breaks in one tick",
            "last=" + compact(meta.lastTickPos) + "/" + meta.lastTickFace
              + ", current=" + compact(pos) + "/" + face,
            3.0D);
          meta.multiBuffer = 1.0D;
        }
      }
    }

    meta.hasBreakThisTick = true;
    meta.lastTickPos = pos;
    meta.lastTickFace = face;
  }

  private void checkFaceGeometry(User user, Meta meta, Vector3i pos, int face) {
    MovementMetadata movement = user.meta().movement();
    if (movement.isInVehicle() || user.meta().abilities().hasViewEntity
      || movement.awaitTeleport || movement.expectTeleport) {
      return;
    }

    double currentX = movement.positionX;
    double currentY = movement.positionY;
    double currentZ = movement.positionZ;
    double lastX = movement.lastPositionX;
    double lastY = movement.lastPositionY;
    double lastZ = movement.lastPositionZ;
    double bukkitEye = user.player().getEyeHeight();
    double[] eyes = {bukkitEye, COMMON_EYE_HEIGHTS[0], COMMON_EYE_HEIGHTS[1], COMMON_EYE_HEIGHTS[2]};

    // If any possible eye point is inside the unit block, vanilla can ray trace through the block
    // to another face. The unit cube is intentionally more permissive than partial collision shapes.
    for (double eye : eyes) {
      if (insideBlock(currentX, currentY + eye, currentZ, pos)
        || insideBlock(lastX, lastY + eye, lastZ, pos)) {
        meta.geometryBuffer = Math.max(0.0D, meta.geometryBuffer - 0.25D);
        return;
      }
    }

    double minX = Math.min(currentX, lastX) - GEOMETRY_TOLERANCE;
    double maxX = Math.max(currentX, lastX) + GEOMETRY_TOLERANCE;
    double minZ = Math.min(currentZ, lastZ) - GEOMETRY_TOLERANCE;
    double maxZ = Math.max(currentZ, lastZ) + GEOMETRY_TOLERANCE;
    double minEyeY = Double.MAX_VALUE;
    double maxEyeY = -Double.MAX_VALUE;
    for (double eye : eyes) {
      minEyeY = Math.min(minEyeY, Math.min(currentY + eye, lastY + eye));
      maxEyeY = Math.max(maxEyeY, Math.max(currentY + eye, lastY + eye));
    }
    minEyeY -= GEOMETRY_TOLERANCE;
    maxEyeY += GEOMETRY_TOLERANCE;

    double bx = pos.x;
    double by = pos.y;
    double bz = pos.z;
    boolean impossible;
    switch (face) {
      case 0: // DOWN
        impossible = minEyeY > by;
        break;
      case 1: // UP
        impossible = maxEyeY < by + 1.0D;
        break;
      case 2: // NORTH (-Z)
        impossible = minZ > bz;
        break;
      case 3: // SOUTH (+Z)
        impossible = maxZ < bz + 1.0D;
        break;
      case 4: // WEST (-X)
        impossible = minX > bx;
        break;
      case 5: // EAST (+X)
        impossible = maxX < bx + 1.0D;
        break;
      default:
        impossible = false;
    }

    if (!impossible) {
      meta.geometryBuffer = Math.max(0.0D, meta.geometryBuffer - 0.2D);
      return;
    }

    meta.geometryBuffer += 0.75D;
    if (meta.geometryBuffer >= 2.25D) {
      flag(user, "impossible break face",
        "face=" + face + ", block=" + compact(pos), 2.0D);
      meta.geometryBuffer = 1.0D;
    }
  }

  private void flag(User user, String message, String details, double vl) {
    Violation violation = Violation.builderFor(BreakSpeedLimiter.class)
      .forPlayer(user.player())
      .withCheckName("FastBreak")
      .withMessage(message)
      .withDetails(details)
      .withVL(vl)
      .build();
    Modules.violationProcessor().processViolation(violation);
  }

  private static boolean isMiningAction(DiggingAction action) {
    return action == DiggingAction.START_DIGGING
      || action == DiggingAction.CANCELLED_DIGGING
      || action == DiggingAction.FINISHED_DIGGING;
  }

  private static boolean tickingReliably(User user) {
    double average = user.meta().connection().averageMovementPacketTimestamp();
    return average > 0.0D && average <= 90.0D;
  }

  private static boolean insideBlock(double x, double y, double z, Vector3i pos) {
    return x >= pos.x && x <= pos.x + 1.0D
      && y >= pos.y && y <= pos.y + 1.0D
      && z >= pos.z && z <= pos.z + 1.0D;
  }

  private static boolean same(Vector3i a, Vector3i b) {
    return a != null && b != null && a.x == b.x && a.y == b.y && a.z == b.z;
  }

  private static String compact(Vector3i pos) {
    return pos == null ? "null" : pos.x + "/" + pos.y + "/" + pos.z;
  }

  public static final class Meta extends CheckCustomMetadata {
    private Vector3i activePos;
    private Vector3i cancelledPos;
    private int activeFace = -1;
    private double stateBuffer;
    private double cancelFaceBuffer;
    private boolean hasBreakThisTick;
    private Vector3i lastTickPos;
    private int lastTickFace = -1;
    private double multiBuffer;
    private double geometryBuffer;
  }
}

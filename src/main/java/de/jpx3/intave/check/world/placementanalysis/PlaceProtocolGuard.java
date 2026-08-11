package de.jpx3.intave.check.world.placementanalysis;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.world.PlacementAnalysis;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.PacketTypes;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.GameMode;
import org.bukkit.Material;

import java.util.ArrayDeque;
import java.util.Deque;

import static de.jpx3.intave.check.world.PlacementAnalysis.COMMON_FLAG_MESSAGE;
import static de.jpx3.intave.module.linker.packet.ListenerPriority.LOWEST;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

/**
 * Raw placement protocol/state checks layered on PlacementAnalysis. It keeps a tiny compensated
 * break history so instant break + place in the same client tick does not look like placement
 * against air, matching the edge case handled by modern prediction anti-cheats.
 */
public final class PlaceProtocolGuard extends MetaCheckPart<PlacementAnalysis, PlaceProtocolGuard.Meta> {
  private static final double GEOMETRY_TOLERANCE = 0.35D;
  private static final double[] COMMON_EYE_HEIGHTS = {1.62D, 1.27D, 0.40D};

  public PlaceProtocolGuard(PlacementAnalysis parentCheck) {
    super(parentCheck, Meta.class);
  }

  @PacketSubscription(priority = LOWEST, packetsIn = BLOCK_DIG, ignoreCancelled = false)
  public void dig(ProtocolPacketEvent event) {
    if (!(event.delegate() instanceof PacketReceiveEvent)) return;
    WrapperPlayClientPlayerDigging dig = new WrapperPlayClientPlayerDigging((PacketReceiveEvent) event.delegate());
    if (dig.getAction() != DiggingAction.START_DIGGING || dig.getBlockPosition() == null) {
      return;
    }

    User user = userOf(event.getPlayer());
    Vector3i pos = dig.getBlockPosition();
    Material oldType;
    try {
      oldType = user.blockCache().typeAt(pos.x, pos.y, pos.z);
    } catch (RuntimeException ignored) {
      return;
    }
    Meta meta = metaOf(user);
    if (meta.breakHistory.size() >= 8) {
      meta.breakHistory.removeFirst();
    }
    meta.breakHistory.addLast(new OldSupport(new Vector3i(pos.x, pos.y, pos.z), oldType, meta.tick));
  }

  @PacketSubscription(priority = LOWEST, packetsIn = {BLOCK_PLACE, USE_ITEM_ON}, ignoreCancelled = false)
  public void place(ProtocolPacketEvent event) {
    if (!(event.delegate() instanceof PacketReceiveEvent)) return;
    User user = userOf(event.getPlayer());
    WrapperPlayClientPlayerBlockPlacement place = new WrapperPlayClientPlayerBlockPlacement((PacketReceiveEvent) event.delegate());
    Vector3i pos = place.getBlockPosition();
    if (pos == null) return;

    int face = place.getFaceId();
    if (face < 0 || face > 5) {
      event.setCancelled(true);
      flag(user, "impossible placement face", "face=" + face + ", support=" + compact(pos), 10.0D);
      return;
    }

    Meta meta = metaOf(user);
    Material support = null;
    try {
      support = user.blockCache().typeAt(pos.x, pos.y, pos.z);
    } catch (RuntimeException ignored) {
    }

    if (!user.meta().abilities().inGameMode(GameMode.CREATIVE) && invalidSupport(support)) {
      boolean recentValidSupport = false;
      for (OldSupport old : meta.breakHistory) {
        if (meta.tick - old.tick <= 2 && same(old.pos, pos) && !invalidSupport(old.type)) {
          recentValidSupport = true;
          break;
        }
      }
      if (!recentValidSupport) {
        meta.airLiquidBuffer += 1.0D;
        if (meta.airLiquidBuffer >= 2.0D) {
          flag(user, "placed against invalid support",
            "support=" + (support == null ? "unknown" : support.name()) + ", pos=" + compact(pos), 4.0D);
          meta.airLiquidBuffer = 1.0D;
        }
      }
    } else {
      meta.airLiquidBuffer = Math.max(0.0D, meta.airLiquidBuffer - 0.25D);
    }

    if (support == null || "SCAFFOLDING".equals(support.name())) {
      return;
    }
    checkFaceGeometry(user, meta, pos, face);
  }

  @PacketSubscription(
    priority = LOWEST,
    packetsIn = {FLYING, LOOK, POSITION, POSITION_LOOK, CLIENT_TICK_END},
    ignoreCancelled = false
  )
  public void tick(ProtocolPacketEvent event) {
    User user = userOf(event.getPlayer());
    PacketTypeCommon type = event.getPacketType();
    boolean modernBoundary = user.meta().protocol().sendsClientTickEnd();
    boolean boundary = modernBoundary ? PacketTypes.isClientEndTick(type) : !PacketTypes.isClientEndTick(type);
    if (!boundary) return;

    Meta meta = metaOf(user);
    meta.tick++;
    while (!meta.breakHistory.isEmpty() && meta.tick - meta.breakHistory.peekFirst().tick > 2) {
      meta.breakHistory.removeFirst();
    }
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
    double[] eyes = {user.player().getEyeHeight(), COMMON_EYE_HEIGHTS[0], COMMON_EYE_HEIGHTS[1], COMMON_EYE_HEIGHTS[2]};

    for (double eye : eyes) {
      if (insideBlock(currentX, currentY + eye, currentZ, pos)
        || insideBlock(lastX, lastY + eye, lastZ, pos)) {
        meta.positionBuffer = Math.max(0.0D, meta.positionBuffer - 0.25D);
        return;
      }
    }

    double minX = Math.min(currentX, lastX) - GEOMETRY_TOLERANCE;
    double maxX = Math.max(currentX, lastX) + GEOMETRY_TOLERANCE;
    double minZ = Math.min(currentZ, lastZ) - GEOMETRY_TOLERANCE;
    double maxZ = Math.max(currentZ, lastZ) + GEOMETRY_TOLERANCE;
    double minY = Double.MAX_VALUE;
    double maxY = -Double.MAX_VALUE;
    for (double eye : eyes) {
      minY = Math.min(minY, Math.min(currentY + eye, lastY + eye));
      maxY = Math.max(maxY, Math.max(currentY + eye, lastY + eye));
    }
    minY -= GEOMETRY_TOLERANCE;
    maxY += GEOMETRY_TOLERANCE;

    boolean impossible;
    switch (face) {
      case 0: impossible = minY > pos.y; break;
      case 1: impossible = maxY < pos.y + 1.0D; break;
      case 2: impossible = minZ > pos.z; break;
      case 3: impossible = maxZ < pos.z + 1.0D; break;
      case 4: impossible = minX > pos.x; break;
      case 5: impossible = maxX < pos.x + 1.0D; break;
      default: impossible = false;
    }

    if (!impossible) {
      meta.positionBuffer = Math.max(0.0D, meta.positionBuffer - 0.2D);
      return;
    }
    meta.positionBuffer += 0.75D;
    if (meta.positionBuffer >= 2.25D) {
      flag(user, "placed against hidden face", "face=" + face + ", support=" + compact(pos), 2.0D);
      meta.positionBuffer = 1.0D;
    }
  }

  private void flag(User user, String message, String details, double vl) {
    Violation violation = Violation.builderFor(PlacementAnalysis.class)
      .forPlayer(user.player())
      .withCheckName("Scaffold")
      .withMessage(COMMON_FLAG_MESSAGE)
      .withDetails(message + ": " + details)
      .withCustomThreshold(PlacementAnalysis.legacyConfigurationLayout()
        ? "thresholds" : "cloud-thresholds.on-premise")
      .withVL(vl)
      .build();
    Modules.violationProcessor().processViolation(violation);
  }

  private static boolean invalidSupport(Material type) {
    return type == null || isAir(type) || type == Material.WATER || type == Material.LAVA
      || "BUBBLE_COLUMN".equals(type.name());
  }

  private static boolean isAir(Material type) {
    String name = type.name();
    return "AIR".equals(name) || "CAVE_AIR".equals(name) || "VOID_AIR".equals(name);
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
    return pos.x + "/" + pos.y + "/" + pos.z;
  }

  private static final class OldSupport {
    private final Vector3i pos;
    private final Material type;
    private final int tick;

    private OldSupport(Vector3i pos, Material type, int tick) {
      this.pos = pos;
      this.type = type;
      this.tick = tick;
    }
  }

  public static final class Meta extends CheckCustomMetadata {
    private final Deque<OldSupport> breakHistory = new ArrayDeque<>();
    private int tick;
    private double airLiquidBuffer;
    private double positionBuffer;
  }
}

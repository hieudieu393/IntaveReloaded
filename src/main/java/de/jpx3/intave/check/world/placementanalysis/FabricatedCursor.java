package de.jpx3.intave.check.world.placementanalysis;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.check.CheckPart;
import de.jpx3.intave.check.world.PlacementAnalysis;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.reader.BlockInteractionReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.user.User;
import org.bukkit.util.Vector;

import static de.jpx3.intave.check.world.PlacementAnalysis.COMMON_FLAG_MESSAGE;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.BLOCK_PLACE;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.USE_ITEM_ON;

/**
 * Validates placement cursors against the compensated outline shape of the clicked block. Normal
 * cubes use the strict 0..1 client range; blocks whose outline legitimately exceeds the cube get
 * only the extra range their actual shape requires (capped to the vanilla-safe -0.5..1.5 envelope).
 */
public final class FabricatedCursor extends CheckPart<PlacementAnalysis> {
  private static final double MAX_DOUBLE_ERROR = Math.ulp(30_000_000.0D) * 2.0D;
  private static final double FLOAT_STEP_AT_ONE = Math.ulp(1.0F);

  public FabricatedCursor(PlacementAnalysis parentCheck) {
    super(parentCheck);
  }

  @PacketSubscription(
    ignoreCancelled = false,
    packetsIn = {BLOCK_PLACE, USE_ITEM_ON}
  )
  public void receive(PacketEvent event) {
    User user = userOf(event.getPlayer());
    BlockInteractionReader reader = PacketReaders.readerOf(event.getPacket());
    try {
      Vector cursor = reader.facingVector();
      BlockPosition position = reader.nativeBlockPosition();
      if (cursor == null || position == null) {
        return;
      }

      Bounds bounds = boundsFor(user, position);
      boolean invalid = outside(cursor.getX(), bounds.minX, bounds.maxX)
        || outside(cursor.getY(), bounds.minY, bounds.maxY)
        || outside(cursor.getZ(), bounds.minZ, bounds.maxZ);
      if (!invalid) {
        return;
      }

      Violation violation = Violation.builderFor(PlacementAnalysis.class)
        .forPlayer(event.getPlayer())
        .withCheckName("Scaffold")
        .withMessage(COMMON_FLAG_MESSAGE)
        .withDetails("fabricated cursor ["
          + format(cursor.getX()) + "," + format(cursor.getY()) + "," + format(cursor.getZ())
          + "] bounds=[" + format(bounds.minX) + ".." + format(bounds.maxX)
          + "," + format(bounds.minY) + ".." + format(bounds.maxY)
          + "," + format(bounds.minZ) + ".." + format(bounds.maxZ) + "]")
        .withCustomThreshold(PlacementAnalysis.legacyConfigurationLayout()
          ? "thresholds" : "cloud-thresholds.on-premise")
        .withVL(4)
        .build();
      Modules.violationProcessor().processViolation(violation);
    } finally {
      reader.release();
    }
  }

  private static Bounds boundsFor(User user, BlockPosition position) {
    double minX = 0.0D, minY = 0.0D, minZ = 0.0D;
    double maxX = 1.0D, maxY = 1.0D, maxZ = 1.0D;
    try {
      BlockShape shape = user.blockCache().outlineShapeAt(position);
      if (shape != null && !shape.isEmpty()) {
        minX = saneMin(shape.min(Direction.Axis.X_AXIS));
        minY = saneMin(shape.min(Direction.Axis.Y_AXIS));
        minZ = saneMin(shape.min(Direction.Axis.Z_AXIS));
        maxX = saneMax(shape.max(Direction.Axis.X_AXIS));
        maxY = saneMax(shape.max(Direction.Axis.Y_AXIS));
        maxZ = saneMax(shape.max(Direction.Axis.Z_AXIS));
      }
    } catch (RuntimeException ignored) {
      // Unresolved compensated shape: strict vanilla cube is safer than inventing a larger shape.
    }
    return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
  }

  private static double saneMin(double value) {
    if (!Double.isFinite(value)) return 0.0D;
    return Math.max(-0.5D, Math.min(0.0D, value));
  }

  private static double saneMax(double value) {
    if (!Double.isFinite(value)) return 1.0D;
    return Math.min(1.5D, Math.max(1.0D, value));
  }

  private static boolean outside(double value, double min, double max) {
    return value < min - MAX_DOUBLE_ERROR || value > max + FLOAT_STEP_AT_ONE;
  }

  private static String format(double value) {
    return String.format(java.util.Locale.ROOT, "%.5f", value);
  }

  private static final class Bounds {
    private final double minX, minY, minZ, maxX, maxY, maxZ;

    private Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
      this.minX = minX;
      this.minY = minY;
      this.minZ = minZ;
      this.maxX = maxX;
      this.maxY = maxY;
      this.maxZ = maxZ;
    }
  }
}

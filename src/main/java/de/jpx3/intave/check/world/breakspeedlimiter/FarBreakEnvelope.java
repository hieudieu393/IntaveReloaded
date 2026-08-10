package de.jpx3.intave.check.world.breakspeedlimiter;

import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.world.BreakSpeedLimiter;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.reader.BlockDigReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.world.raytrace.Raytracing;

import static de.jpx3.intave.module.linker.packet.ListenerPriority.LOWEST;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.BLOCK_DIG;

/** Conservative FarBreak envelope layered on top of InteractionRaytrace/BreakSpeedLimiter. */
public final class FarBreakEnvelope extends MetaCheckPart<BreakSpeedLimiter, FarBreakEnvelope.Meta> {
  private static final double[] COMMON_EYE_HEIGHTS = {1.62D, 1.27D, 0.40D};

  public FarBreakEnvelope(BreakSpeedLimiter parentCheck) {
    super(parentCheck, Meta.class);
  }

  @PacketSubscription(priority = LOWEST, packetsIn = BLOCK_DIG, ignoreCancelled = false)
  public void receive(PacketEvent event) {
    User user = userOf(event.getPlayer());
    if (user.meta().movement().isInVehicle() || user.meta().abilities().hasViewEntity) {
      return;
    }

    BlockDigReader reader = PacketReaders.readerOf(event.getPacket());
    try {
      EnumWrappers.PlayerDigType action = reader.action();
      if (action != EnumWrappers.PlayerDigType.START_DESTROY_BLOCK
        && action != EnumWrappers.PlayerDigType.STOP_DESTROY_BLOCK) {
        return;
      }
      BlockPosition block = reader.nativeBlockPosition();
      if (block == null) {
        return;
      }

      MovementMetadata movement = user.meta().movement();
      double minSq = Double.MAX_VALUE;
      double bukkitEye = event.getPlayer().getEyeHeight();
      double[] eyeHeights = {bukkitEye, COMMON_EYE_HEIGHTS[0], COMMON_EYE_HEIGHTS[1], COMMON_EYE_HEIGHTS[2]};
      for (double eye : eyeHeights) {
        minSq = Math.min(minSq, distanceSqToUnitBlock(
          movement.positionX, movement.positionY + eye, movement.positionZ, block));
        minSq = Math.min(minSq, distanceSqToUnitBlock(
          movement.lastPositionX, movement.lastPositionY + eye, movement.lastPositionZ, block));
      }

      double max = Raytracing.blockReachDistanceOf(user) + 0.35D;
      Meta meta = metaOf(user);
      if (minSq <= max * max) {
        meta.buffer = Math.max(0.0D, meta.buffer - 0.25D);
        return;
      }

      meta.buffer += 1.0D;
      if (meta.buffer < 2.0D) {
        return;
      }

      Violation violation = Violation.builderFor(BreakSpeedLimiter.class)
        .forPlayer(user.player())
        .withCheckName("FastBreak")
        .withMessage("broke a block from too far away")
        .withDetails("distance=" + format(Math.sqrt(minSq)) + ", max=" + format(max)
          + ", action=" + action.name())
        .withVL(3.0D)
        .build();
      Modules.violationProcessor().processViolation(violation);
      meta.buffer = 1.0D;
    } finally {
      reader.release();
    }
  }

  private static double distanceSqToUnitBlock(double x, double y, double z, BlockPosition block) {
    double minX = block.getX();
    double minY = block.getY();
    double minZ = block.getZ();
    double dx = axisDistance(x, minX, minX + 1.0D);
    double dy = axisDistance(y, minY, minY + 1.0D);
    double dz = axisDistance(z, minZ, minZ + 1.0D);
    return dx * dx + dy * dy + dz * dz;
  }

  private static double axisDistance(double value, double min, double max) {
    return value < min ? min - value : value > max ? value - max : 0.0D;
  }

  private static String format(double value) {
    return String.format(java.util.Locale.ROOT, "%.3f", value);
  }

  public static final class Meta extends CheckCustomMetadata {
    private double buffer;
  }
}

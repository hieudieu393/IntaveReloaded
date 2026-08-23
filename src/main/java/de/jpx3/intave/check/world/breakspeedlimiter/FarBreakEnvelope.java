package de.jpx3.intave.check.world.breakspeedlimiter;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
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
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.ListenerPriority.LOWEST;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.BLOCK_DIG;

/** Conservative FarBreak envelope layered on top of InteractionRaytrace/BreakSpeedLimiter. */
public final class FarBreakEnvelope extends MetaCheckPart<BreakSpeedLimiter, FarBreakEnvelope.Meta> {
  private static final double[] COMMON_EYE_HEIGHTS = {1.62D, 1.27D, 0.40D};

  public FarBreakEnvelope(BreakSpeedLimiter parentCheck) {
    super(parentCheck, Meta.class);
  }

  @PacketSubscription(priority = LOWEST, packetsIn = BLOCK_DIG, ignoreCancelled = false)
  public void receive(ProtocolPacketEvent event) {
    Player player = (Player) event.getPlayer();
    User user = userOf(player);
    if (user.meta().movement().isInVehicle() || user.meta().abilities().hasViewEntity) {
      return;
    }

    BlockDigReader reader = PacketReaders.readerOf(event);
    try {
      DiggingAction action = reader.action();
      if (action != DiggingAction.START_DIGGING
        && action != DiggingAction.FINISHED_DIGGING) {
        return;
      }
      BlockPosition block = reader.nativeBlockPosition();
      if (block == null) {
        return;
      }

      MovementMetadata movement = user.meta().movement();
      double minSq = Double.MAX_VALUE;
      double bukkitEye = player.getEyeHeight();
      double[] eyeHeights = {bukkitEye, COMMON_EYE_HEIGHTS[0], COMMON_EYE_HEIGHTS[1], COMMON_EYE_HEIGHTS[2]};
      for (double eye : eyeHeights) {
        minSq = Math.min(minSq, distanceSqToUnitBlock(
          movement.positionX, movement.positionY + eye, movement.positionZ, block));
        minSq = Math.min(minSq, distanceSqToUnitBlock(
          movement.lastPositionX, movement.lastPositionY + eye, movement.lastPositionZ, block));
      }

      double max = blockReachDistance(user) + 0.35D;
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

  private static double blockReachDistance(User user) {
    boolean creative = user.meta().abilities().inGameMode(GameMode.CREATIVE);
    double fallback = creative ? 5.0D : 4.5D;
    if (!user.meta().protocol().supportsInteractionRangeAttributes()) {
      return fallback;
    }

    double tracked = user.meta().abilities().attributeValue("player.block_interaction_range");
    if (!Double.isFinite(tracked) || tracked <= 0.0D) {
      return fallback;
    }
    if (creative && Math.abs(tracked - 4.5D) < 1.0E-6D) {
      return fallback;
    }
    return Math.min(tracked, 64.0D);
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

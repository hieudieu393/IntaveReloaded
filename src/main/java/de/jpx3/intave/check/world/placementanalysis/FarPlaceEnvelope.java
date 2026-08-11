package de.jpx3.intave.check.world.placementanalysis;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.world.PlacementAnalysis;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.reader.BlockInteractionReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.GameMode;

import static de.jpx3.intave.check.world.PlacementAnalysis.COMMON_FLAG_MESSAGE;
import static de.jpx3.intave.module.linker.packet.ListenerPriority.LOWEST;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.BLOCK_PLACE;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.USE_ITEM_ON;

/** Independent conservative distance envelope layered on top of InteractionRaytrace. */
public final class FarPlaceEnvelope extends MetaCheckPart<PlacementAnalysis, FarPlaceEnvelope.Meta> {
  private static final double[] COMMON_EYE_HEIGHTS = {1.62D, 1.27D, 0.40D};

  public FarPlaceEnvelope(PlacementAnalysis parentCheck) {
    super(parentCheck, Meta.class);
  }

  @PacketSubscription(priority = LOWEST, packetsIn = {BLOCK_PLACE, USE_ITEM_ON}, ignoreCancelled = false)
  public void receive(ProtocolPacketEvent event) {
    User user = userOf(event.getPlayer());
    if (user.meta().movement().isInVehicle() || user.meta().abilities().hasViewEntity) {
      return;
    }

    BlockInteractionReader reader = PacketReaders.readerOf(event.getPacket());
    try {
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

      Violation violation = Violation.builderFor(PlacementAnalysis.class)
        .forPlayer(user.player())
        .withCheckName("Scaffold")
        .withMessage(COMMON_FLAG_MESSAGE)
        .withDetails("far-place distance=" + format(Math.sqrt(minSq)) + ", max=" + format(max))
        .withCustomThreshold(PlacementAnalysis.legacyConfigurationLayout()
          ? "thresholds" : "cloud-thresholds.on-premise")
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

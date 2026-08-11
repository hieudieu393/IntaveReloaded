package de.jpx3.intave.check.world.placementanalysis;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.world.PlacementAnalysis;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.packet.reader.PlayerMoveReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;

import static de.jpx3.intave.check.world.PlacementAnalysis.COMMON_FLAG_MESSAGE;
import static de.jpx3.intave.module.linker.packet.ListenerPriority.LOWEST;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

/** Experimental Scaffold signal for machine-repeated rotation deltas at placement time. */
public final class DuplicateRotationPlace extends MetaCheckPart<PlacementAnalysis, DuplicateRotationPlace.Meta> {
  public DuplicateRotationPlace(PlacementAnalysis parentCheck) {
    super(parentCheck, Meta.class);
  }

  @PacketSubscription(priority = LOWEST, packetsIn = {FLYING, LOOK, POSITION, POSITION_LOOK}, ignoreCancelled = false)
  public void movement(ProtocolPacketEvent event) {
    User user = userOf(event.getPlayer());
    PlayerMoveReader reader = PacketReaders.readerOf(event.getPacket());
    try {
      if (!reader.hasRotation()) return;
      Meta meta = metaOf(user);
      float yaw = reader.yaw();
      float pitch = reader.pitch();
      if (meta.initialized) {
        meta.deltaYaw = Math.abs(wrapDegrees(yaw - meta.lastYaw));
        meta.deltaPitch = Math.abs(pitch - meta.lastPitch);
        meta.rotated = true;
      }
      meta.lastYaw = yaw;
      meta.lastPitch = pitch;
      meta.initialized = true;
    } finally {
      reader.release();
    }
  }

  @PacketSubscription(priority = LOWEST, packetsIn = {BLOCK_PLACE, USE_ITEM_ON}, ignoreCancelled = false)
  public void place(ProtocolPacketEvent event) {
    User user = userOf(event.getPlayer());
    Meta meta = metaOf(user);
    if (!meta.rotated || user.meta().movement().isInVehicle()) {
      return;
    }

    if (meta.deltaYaw > 2.0F && meta.hasPlacedRotation) {
      float diff = Math.abs(meta.deltaYaw - meta.lastPlacedDeltaYaw);
      if (diff < 0.0001F) {
        meta.buffer += 1.0D;
        if (meta.buffer >= 2.0D) {
          Violation violation = Violation.builderFor(PlacementAnalysis.class)
            .forPlayer(user.player())
            .withCheckName("Scaffold")
            .withMessage(COMMON_FLAG_MESSAGE)
            .withDetails("duplicate rotation delta yaw=" + format(meta.deltaYaw)
              + ", pitch=" + format(meta.deltaPitch) + ", diff=" + format(diff))
            .withCustomThreshold(PlacementAnalysis.legacyConfigurationLayout()
              ? "thresholds" : "cloud-thresholds.on-premise")
            .withVL(2.0D)
            .build();
          Modules.violationProcessor().processViolation(violation);
          meta.buffer = 1.0D;
        }
      } else {
        meta.buffer = Math.max(0.0D, meta.buffer - 0.2D);
      }
    } else {
      meta.buffer = Math.max(0.0D, meta.buffer - 0.1D);
    }

    meta.lastPlacedDeltaYaw = meta.deltaYaw;
    meta.hasPlacedRotation = true;
    meta.rotated = false;
  }

  private static float wrapDegrees(float value) {
    value %= 360.0F;
    if (value >= 180.0F) value -= 360.0F;
    if (value < -180.0F) value += 360.0F;
    return value;
  }

  private static String format(double value) {
    return String.format(java.util.Locale.ROOT, "%.6f", value);
  }

  public static final class Meta extends CheckCustomMetadata {
    private boolean initialized;
    private boolean rotated;
    private float lastYaw;
    private float lastPitch;
    private float deltaYaw;
    private float deltaPitch;
    private boolean hasPlacedRotation;
    private float lastPlacedDeltaYaw;
    private double buffer;
  }
}

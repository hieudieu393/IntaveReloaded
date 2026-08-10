package de.jpx3.intave.check.world.placementanalysis;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.CheckPart;
import de.jpx3.intave.check.world.PlacementAnalysis;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.reader.BlockInteractionReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import org.bukkit.util.Vector;

import static de.jpx3.intave.check.world.PlacementAnalysis.COMMON_FLAG_MESSAGE;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.BLOCK_PLACE;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.USE_ITEM_ON;

/**
 * Detects clearly fabricated block-interaction cursor coordinates. Some vanilla block shapes can
 * legitimately extend outside the normal 0..1 cube, so this intentionally uses the conservative
 * universal -0.5..1.5 envelope rather than guessing the clicked block shape in this packet layer.
 */
public final class FabricatedCursor extends CheckPart<PlacementAnalysis> {
  private static final double MAX_DOUBLE_ERROR = Math.ulp(30_000_000.0D) * 2.0D;
  private static final double FLOAT_STEP_AT_ONE = Math.ulp(1.0F);
  private static final double MIN_BOUND = -0.5D;
  private static final double MAX_BOUND = 1.5D;

  public FabricatedCursor(PlacementAnalysis parentCheck) {
    super(parentCheck);
  }

  @PacketSubscription(
    ignoreCancelled = false,
    packetsIn = {BLOCK_PLACE, USE_ITEM_ON}
  )
  public void receive(PacketEvent event) {
    BlockInteractionReader reader = PacketReaders.readerOf(event.getPacket());
    try {
      Vector cursor = reader.facingVector();
      if (cursor == null) {
        return;
      }

      double lower = MIN_BOUND - MAX_DOUBLE_ERROR;
      double upper = MAX_BOUND + FLOAT_STEP_AT_ONE;
      boolean invalid = cursor.getX() < lower || cursor.getY() < lower || cursor.getZ() < lower
        || cursor.getX() > upper || cursor.getY() > upper || cursor.getZ() > upper;
      if (!invalid) {
        return;
      }

      Violation violation = Violation.builderFor(PlacementAnalysis.class)
        .forPlayer(event.getPlayer())
        .withMessage(COMMON_FLAG_MESSAGE)
        .withDetails("fabricated cursor ["
          + format(cursor.getX()) + "," + format(cursor.getY()) + "," + format(cursor.getZ()) + "]")
        .withCustomThreshold(PlacementAnalysis.legacyConfigurationLayout()
          ? "thresholds" : "cloud-thresholds.on-premise")
        .withVL(4)
        .build();
      Modules.violationProcessor().processViolation(violation);
    } finally {
      reader.release();
    }
  }

  private static String format(double value) {
    return String.format("%.5f", value);
  }
}

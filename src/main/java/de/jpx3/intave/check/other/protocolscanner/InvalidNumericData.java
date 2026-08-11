package de.jpx3.intave.check.other.protocolscanner;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.check.CheckPart;
import de.jpx3.intave.check.other.ProtocolScanner;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.reader.BlockInteractionReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.packet.reader.PlayerMoveReader;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

/**
 * Rejects numeric values that a vanilla client cannot produce. Geometric placement/reach
 * validation remains owned by InteractionRaytrace and Physics.
 */
public final class InvalidNumericData extends CheckPart<ProtocolScanner> {
  private static final double HARD_WORLD_BORDER = 2.9999999E7D;

  public InvalidNumericData(ProtocolScanner parentCheck) {
    super(parentCheck);
  }

  @PacketSubscription(ignoreCancelled = false, packetsIn = {FLYING, LOOK, POSITION, POSITION_LOOK})
  public void receiveMovement(ProtocolPacketEvent event) {
    PlayerMoveReader reader = PacketReaders.readerOf(event);
    try {
      if (reader.anyNaNOrInfiniteValue()) {
        flagAndCancel(event, "sent non-finite movement data");
        return;
      }
      if (reader.hasMovement()) {
        double x = reader.positionX();
        double y = reader.positionY();
        double z = reader.positionZ();
        if (Math.abs(x) > HARD_WORLD_BORDER || Math.abs(z) > HARD_WORLD_BORDER || Math.abs(y) > Integer.MAX_VALUE) {
          flagAndCancel(event, "sent position outside valid world bounds");
        }
      }
    } finally {
      reader.release();
    }
  }

  @PacketSubscription(ignoreCancelled = false, packetsIn = {BLOCK_PLACE, USE_ITEM_ON})
  public void receiveInteraction(ProtocolPacketEvent event) {
    BlockInteractionReader reader = PacketReaders.readerOf(event);
    try {
      Vector cursor = reader.facingVector();
      if (cursor != null && (!Double.isFinite(cursor.getX()) || !Double.isFinite(cursor.getY()) || !Double.isFinite(cursor.getZ()))) {
        flagAndCancel(event, "sent non-finite block cursor");
      }
    } finally {
      reader.release();
    }
  }

  private void flagAndCancel(ProtocolPacketEvent event, String details) {
    Player player = event.getPlayer();
    event.setCancelled(true);
    Violation violation = Violation.builderFor(ProtocolScanner.class)
      .forPlayer(player)
      .withMessage("sent invalid packet data")
      .withDetails(details)
      .withVL(100)
      .build();
    Modules.violationProcessor().processViolation(violation);
  }
}

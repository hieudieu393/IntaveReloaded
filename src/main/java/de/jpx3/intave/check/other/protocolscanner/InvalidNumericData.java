package de.jpx3.intave.check.other.protocolscanner;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import de.jpx3.intave.check.CheckPart;
import de.jpx3.intave.check.other.ProtocolScanner;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.packet.reader.PlayerMoveReader;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

/**
 * Rejects numeric values that a vanilla client cannot produce. This intentionally focuses on
 * non-finite data only; geometric placement/reach validation remains owned by InteractionRaytrace.
 */
public final class InvalidNumericData extends CheckPart<ProtocolScanner> {
  public InvalidNumericData(ProtocolScanner parentCheck) {
    super(parentCheck);
  }

  @PacketSubscription(
    ignoreCancelled = false,
    packetsIn = {FLYING, LOOK, POSITION, POSITION_LOOK}
  )
  public void receiveMovement(PacketEvent event) {
    PlayerMoveReader reader = PacketReaders.readerOf(event.getPacket());
    try {
      if (reader.anyNaNOrInfiniteValue()) {
        flagAndCancel(event, "sent non-finite movement data");
      }
    } finally {
      reader.release();
    }
  }

  @PacketSubscription(
    ignoreCancelled = false,
    packetsIn = {BLOCK_PLACE, USE_ITEM_ON}
  )
  public void receiveInteraction(PacketEvent event) {
    PacketContainer packet = event.getPacket();
    StructureModifier<Float> cursor = packet.getFloat();
    int limit = Math.min(3, cursor.size());
    for (int i = 0; i < limit; i++) {
      Float value = cursor.readSafely(i);
      if (value != null && !Float.isFinite(value)) {
        flagAndCancel(event, "sent non-finite block cursor");
        return;
      }
    }
  }

  private void flagAndCancel(PacketEvent event, String details) {
    Player player = event.getPlayer();
    if (event.isReadOnly()) {
      event.setReadOnly(false);
    }
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

package de.jpx3.intave.check.world.placementanalysis;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.check.CheckPart;
import de.jpx3.intave.check.world.PlacementAnalysis;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.reader.BlockInteractionReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.ViolationMetadata;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.util.Vector;

import static de.jpx3.intave.check.world.PlacementAnalysis.COMMON_FLAG_MESSAGE;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.BLOCK_PLACE;
import static de.jpx3.intave.module.violation.Violation.ViolationFlags.DISPLAY_IN_ALL_VERBOSE_MODES;

public final class Facing extends CheckPart<PlacementAnalysis> {
  public Facing(PlacementAnalysis parentCheck) {
    super(parentCheck);
  }

  @PacketSubscription(packetsIn = BLOCK_PLACE)
  public void checkPlacementVector(ProtocolPacketEvent event) {
    Player player = (Player) event.getPlayer();
    BlockInteractionReader reader = PacketReaders.readerOf(event);
    try {
      if (reader.enumDirection() == 255) {
        return;
      }
      Vector cursor = reader.facingVector();
      if (cursor == null) {
        return;
      }
      double x = cursor.getX();
      double y = cursor.getY();
      double z = cursor.getZ();
      if (x < 0 || y < 0 || z < 0 || x > 1 || y > 1 || z > 1) {
        Violation violation = Violation.builderFor(PlacementAnalysis.class)
          .forPlayer(player).withMessage(COMMON_FLAG_MESSAGE)
          .withCustomThreshold(PlacementAnalysis.legacyConfigurationLayout() ? "thresholds" : "cloud-thresholds.on-premise")
          .withVL(5).build();
        Modules.violationProcessor().processViolation(violation);
      }
    } finally {
      reader.release();
    }
  }

  @BukkitEventSubscription(ignoreCancelled = true)
  public void onPlace(BlockPlaceEvent place) {
    Player player = place.getPlayer();
    User user = userOf(player);
    MetadataBundle meta = user.meta();
    ViolationMetadata violationMetadata = meta.violationLevel();
    if (place.isCancelled()) {
      violationMetadata.facingFailedCounter = -10;
    }
    int facingFailedCounter = violationMetadata.facingFailedCounter;
    if (facingFailedCounter > 3) {
      Violation.builderFor(PlacementAnalysis.class)
        .forPlayer(player)
        .withMessage(COMMON_FLAG_MESSAGE)
        .appendFlags(DISPLAY_IN_ALL_VERBOSE_MODES)
        .withDetails("repeated placement faults")
        .withVL(0)
        .build();
    }
  }
}

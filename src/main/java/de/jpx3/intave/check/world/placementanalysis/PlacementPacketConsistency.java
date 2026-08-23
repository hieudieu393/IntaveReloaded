package de.jpx3.intave.check.world.placementanalysis;

import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
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
import org.bukkit.util.Vector;

import static de.jpx3.intave.check.world.PlacementAnalysis.COMMON_FLAG_MESSAGE;
import static de.jpx3.intave.module.linker.packet.ListenerPriority.LOWEST;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

/**
 * Detects repeated, materially different placement packets inside one client movement tick.
 * The result is buffered and never cancels a single placement on its own.
 */
public final class PlacementPacketConsistency extends MetaCheckPart<PlacementAnalysis, PlacementPacketConsistency.Meta> {
  public PlacementPacketConsistency(PlacementAnalysis parentCheck) {
    super(parentCheck, Meta.class);
  }

  @PacketSubscription(
    priority = LOWEST,
    ignoreCancelled = false,
    packetsIn = {BLOCK_PLACE, USE_ITEM_ON}
  )
  public void receivePlacement(ProtocolPacketEvent event) {
    User user = userOf(event.getPlayer());
    BlockInteractionReader reader = PacketReaders.readerOf(event);
    try {
      BlockPosition position = reader.nativeBlockPosition();
      int direction = reader.enumDirection();
      Vector cursor = reader.facingVector();
      if (position == null || direction == 255) {
        return;
      }

      Meta meta = metaOf(user);
      if (meta.hasPlacement && materiallyDifferent(meta, position, direction, cursor)) {
        if (tickingReliably(user)) {
          meta.buffer += 1.0;
          if (meta.buffer > 2.0) {
            Violation violation = Violation.builderFor(PlacementAnalysis.class)
              .forPlayer(event.getPlayer())
              .withMessage(COMMON_FLAG_MESSAGE)
              .withDetails("multiple different placements in one movement tick")
              .withCustomThreshold(PlacementAnalysis.legacyConfigurationLayout()
                ? "thresholds" : "cloud-thresholds.on-premise")
              .withVL(4)
              .build();
            Modules.violationProcessor().processViolation(violation);
            meta.buffer = Math.max(1.0, meta.buffer - 0.75);
          }
        }
      }

      meta.hasPlacement = true;
      meta.x = position.getX();
      meta.y = position.getY();
      meta.z = position.getZ();
      meta.direction = direction;
      if (cursor != null) {
        meta.cursorX = cursor.getX();
        meta.cursorY = cursor.getY();
        meta.cursorZ = cursor.getZ();
        meta.hasCursor = true;
      } else {
        meta.hasCursor = false;
      }
    } finally {
      reader.release();
    }
  }

  @PacketSubscription(
    priority = LOWEST,
    ignoreCancelled = false,
    packetsIn = {FLYING, LOOK, POSITION, POSITION_LOOK, CLIENT_TICK_END}
  )
  public void receiveTick(ProtocolPacketEvent event) {
    Meta meta = metaOf(userOf(event.getPlayer()));
    meta.hasPlacement = false;
    if (meta.buffer > 0) {
      meta.buffer = Math.max(0.0, meta.buffer - 0.08);
    }
  }

  private static boolean materiallyDifferent(Meta meta, BlockPosition position, int direction, Vector cursor) {
    if (meta.x != position.getX() || meta.y != position.getY() || meta.z != position.getZ() || meta.direction != direction) {
      return true;
    }
    if (!meta.hasCursor || cursor == null) {
      return meta.hasCursor != (cursor != null);
    }
    return Math.abs(meta.cursorX - cursor.getX()) > 1.0E-5
      || Math.abs(meta.cursorY - cursor.getY()) > 1.0E-5
      || Math.abs(meta.cursorZ - cursor.getZ()) > 1.0E-5;
  }

  private static boolean tickingReliably(User user) {
    double average = user.meta().connection().averageMovementPacketTimestamp();
    return average > 0 && average <= 90;
  }

  public static final class Meta extends CheckCustomMetadata {
    private boolean hasPlacement;
    private int x, y, z, direction;
    private boolean hasCursor;
    private double cursorX, cursorY, cursorZ;
    private double buffer;
  }
}

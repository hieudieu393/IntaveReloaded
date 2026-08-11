package de.jpx3.intave.check.other.protocolscanner;

import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.check.CheckPart;
import de.jpx3.intave.check.other.ProtocolScanner;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.reader.BlockDigReader;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.ProtocolMetadata;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.BLOCK_DIG;

/**
 * Modern vanilla clients use the digging packet for several non-block actions (drop item,
 * release-use-item, offhand swap, etc). For those actions the block fields are placeholders:
 * origin position, DOWN face and, on native sequence-capable clients, sequence zero.
 */
public final class InvalidDigData extends CheckPart<ProtocolScanner> {
  public InvalidDigData(ProtocolScanner parentCheck) {
    super(parentCheck);
  }

  @PacketSubscription(
    ignoreCancelled = false,
    packetsIn = BLOCK_DIG
  )
  public void receiveDig(User user, BlockDigReader reader, ProtocolPacketEvent event) {
    DiggingAction action = reader.action();
    if (action == null || isBlockMiningAction(action)) {
      return;
    }

    BlockPosition position = reader.nativeBlockPosition();
    EnumWrappers.Direction face = event.getPacket().getDirections().readSafely(0);
    Integer sequence = event.getPacket().getIntegers().readSafely(0);

    // Do not guess fields the compatibility layer failed to expose.
    if (position == null || face == null) {
      return;
    }

    boolean nativeSequence = user.protocolVersion() >= ProtocolMetadata.VER_1_19_2;
    if (nativeSequence && sequence == null) {
      return;
    }

    boolean origin = position.getX() == 0 && position.getY() == 0 && position.getZ() == 0;
    boolean sequenceValid = !nativeSequence || sequence == 0;
    boolean valid = origin && face == EnumWrappers.Direction.DOWN && sequenceValid;
    if (valid) {
      return;
    }

    if (event.isReadOnly()) {
      event.setReadOnly(false);
    }
    event.setCancelled(true);

    int vl = parentCheck().configuration().settings().intBy("invalid-dig-vl", 5);
    Violation violation = Violation.builderFor(ProtocolScanner.class)
      .forPlayer(user.player())
      .withMessage("sent impossible auxiliary dig data")
      .withDetails(action.name() + " pos=" + position + ", face=" + face
        + ", sequence=" + (nativeSequence ? String.valueOf(sequence) : "legacy-exempt"))
      .withVL(Math.max(1, vl))
      .build();
    Modules.violationProcessor().processViolation(violation);
  }

  private static boolean isBlockMiningAction(DiggingAction action) {
    return action == DiggingAction.START_DESTROY_BLOCK
      || action == DiggingAction.STOP_DESTROY_BLOCK
      || action == DiggingAction.ABORT_DESTROY_BLOCK;
  }
}

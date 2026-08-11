package de.jpx3.intave.check.other.protocolscanner;

import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.other.ProtocolScanner;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketId;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.reader.BlockDigReader;
import de.jpx3.intave.packet.reader.BlockInteractionReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;

import static de.jpx3.intave.module.linker.packet.ListenerPriority.LOW;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

/**
 * Validates the shared interaction sequence counter introduced in modern clients. The counter is
 * used by block acknowledgements, so impossible jumps/replays are useful protocol signals. We do
 * not cancel on an ordinary mismatch: proxies translating protocols are given a buffered tolerance.
 */
public final class SequenceGuard extends MetaCheckPart<ProtocolScanner, SequenceGuard.Meta> {
  private static final int MIN_SEQUENCE_PROTOCOL = ProtocolMetadata.VER_1_19_2;

  public SequenceGuard(ProtocolScanner parentCheck) {
    super(parentCheck, Meta.class);
  }

  @PacketSubscription(
    priority = LOW,
    ignoreCancelled = false,
    packetsIn = {BLOCK_PLACE, USE_ITEM_ON}
  )
  public void receiveBlockInteraction(PacketEvent event) {
    User user = userOf(event.getPlayer());
    if (!applicable(user)) {
      return;
    }

    BlockInteractionReader reader = PacketReaders.readerOf(event.getPacket());
    try {
      int sequence = reader.sequenceNumber(user);
      if (sequence < 0) {
        hardInvalid(user, event, event.getPacketType().name(), sequence);
        return;
      }
      validate(user, sequence, event.getPacketType().name());
    } finally {
      reader.release();
    }
  }

  @PacketSubscription(
    priority = LOW,
    ignoreCancelled = false,
    packetsIn = USE_ITEM
  )
  public void receiveUseItem(PacketEvent event) {
    User user = userOf(event.getPlayer());
    if (!applicable(user)) {
      return;
    }
    Integer sequence = event.getPacket().getIntegers().readSafely(0);
    if (sequence != null) {
      if (sequence < 0) {
        hardInvalid(user, event, "USE_ITEM", sequence);
        return;
      }
      validate(user, sequence, "USE_ITEM");
    }
  }

  @PacketSubscription(
    priority = LOW,
    ignoreCancelled = false,
    packetsIn = BLOCK_DIG
  )
  public void receiveDig(PacketEvent event) {
    User user = userOf(event.getPlayer());
    if (!applicable(user)) {
      return;
    }

    BlockDigReader reader = PacketReaders.readerOf(event.getPacket());
    try {
      EnumWrappers.PlayerDigType action = reader.action();
      if (action != EnumWrappers.PlayerDigType.START_DESTROY_BLOCK
        && action != EnumWrappers.PlayerDigType.STOP_DESTROY_BLOCK) {
        return;
      }

      Integer sequence = event.getPacket().getIntegers().readSafely(0);
      if (sequence != null) {
        String source = "BLOCK_DIG/" + action.name();
        if (sequence < 0) {
          hardInvalid(user, event, source, sequence);
          return;
        }
        validate(user, sequence, source);
      }
    } finally {
      reader.release();
    }
  }

  @PacketSubscription(
    ignoreCancelled = false,
    packetsOut = {PacketId.Server.POSITION, PacketId.Server.RESPAWN}
  )
  public void resetOnWorldState(PacketEvent event) {
    Meta meta = metaOf(userOf(event.getPlayer()));
    meta.initialized = false;
    meta.lastSequence = 0;
    meta.buffer = 0.0;
  }

  private void hardInvalid(User user, PacketEvent event, String source, int sequence) {
    if (event.isReadOnly()) {
      event.setReadOnly(false);
    }
    event.setCancelled(true);

    Violation violation = Violation.builderFor(ProtocolScanner.class)
      .forPlayer(user.player())
      .withCheckName("BadPackets")
      .withMessage("sent a negative interaction sequence")
      .withDetails(source + " sequence=" + sequence)
      .withVL(20.0D)
      .build();
    Modules.violationProcessor().processViolation(violation);
  }

  private void validate(User user, int sequence, String source) {
    Meta meta = metaOf(user);
    if (!meta.initialized) {
      meta.initialized = true;
      meta.lastSequence = sequence;
      return;
    }

    int expected = meta.lastSequence + 1;
    meta.lastSequence = sequence;
    if (sequence == expected) {
      meta.buffer = Math.max(0.0, meta.buffer - 0.25);
      return;
    }

    meta.buffer += 1.0;
    if (meta.buffer < 2.0) {
      return;
    }

    int vl = parentCheck().configuration().settings().intBy("packet-order-vl", 5);
    Violation violation = Violation.builderFor(ProtocolScanner.class)
      .forPlayer(user.player())
      .withCheckName("BadPackets")
      .withMessage("sent an unexpected interaction sequence")
      .withDetails(source + " expected=" + expected + ", got=" + sequence)
      .withVL(Math.max(1, vl))
      .build();
    Modules.violationProcessor().processViolation(violation);
    meta.buffer = 1.0;
  }

  private static boolean applicable(User user) {
    return user.protocolVersion() >= MIN_SEQUENCE_PROTOCOL;
  }

  public static final class Meta extends CheckCustomMetadata {
    private boolean initialized;
    private int lastSequence;
    private double buffer;
  }
}

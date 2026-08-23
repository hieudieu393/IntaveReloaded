package de.jpx3.intave.check.other.protocolscanner;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.other.ProtocolScanner;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.CLIENT_TICK_END;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.PLAYER_INPUT;

/** 1.21.2+ clients should not send more than one PLAYER_INPUT inside a client tick. */
public final class DuplicateInputGuard extends MetaCheckPart<ProtocolScanner, DuplicateInputGuard.Meta> {
  public DuplicateInputGuard(ProtocolScanner parentCheck) {
    super(parentCheck, Meta.class);
  }

  @PacketSubscription(packetsIn = CLIENT_TICK_END, ignoreCancelled = false)
  public void tickEnd(ProtocolPacketEvent event) {
    Meta meta = metaOf(userOf(event.getPlayer()));
    meta.sentInput = false;
    meta.buffer = Math.max(0.0, meta.buffer - 0.05);
  }

  @PacketSubscription(packetsIn = PLAYER_INPUT, ignoreCancelled = false)
  public void input(ProtocolPacketEvent event) {
    User user = userOf(event.getPlayer());
    if (user.protocolVersion() < ProtocolMetadata.VER_1_21_3) {
      return;
    }
    Meta meta = metaOf(user);
    if (!meta.sentInput) {
      meta.sentInput = true;
      return;
    }

    meta.buffer += 1.0;
    if (meta.buffer < 2.0) {
      return;
    }

    Violation violation = Violation.builderFor(ProtocolScanner.class)
      .forPlayer(user.player())
      .withCheckName("BadPackets")
      .withMessage("sent duplicate player input in one tick")
      .withDetails("protocol=" + user.protocolVersion())
      .withVL(5.0)
      .build();
    Modules.violationProcessor().processViolation(violation);
    meta.buffer = 1.0;
  }

  public static final class Meta extends CheckCustomMetadata {
    private boolean sentInput;
    private double buffer;
  }
}

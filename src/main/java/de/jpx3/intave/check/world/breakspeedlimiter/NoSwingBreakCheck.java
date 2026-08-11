package de.jpx3.intave.check.world.breakspeedlimiter;

import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.world.BreakSpeedLimiter;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.PacketTypes;
import de.jpx3.intave.packet.reader.BlockDigReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;

import static de.jpx3.intave.module.linker.packet.ListenerPriority.LOWEST;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

/** Supplemental FastBreak signal: a real break sequence should contain a swing in the same client tick. */
public final class NoSwingBreakCheck extends MetaCheckPart<BreakSpeedLimiter, NoSwingBreakCheck.Meta> {
  public NoSwingBreakCheck(BreakSpeedLimiter parentCheck) {
    super(parentCheck, Meta.class);
  }

  @PacketSubscription(priority = LOWEST, packetsIn = ARM_ANIMATION, ignoreCancelled = false)
  public void animation(ProtocolPacketEvent event) {
    metaOf(userOf(event.getPlayer())).sentAnimation = true;
  }

  @PacketSubscription(priority = LOWEST, packetsIn = BLOCK_DIG, ignoreCancelled = false)
  public void dig(ProtocolPacketEvent event) {
    BlockDigReader reader = PacketReaders.readerOf(event.getPacket());
    try {
      DiggingAction action = reader.action();
      if (action == DiggingAction.START_DESTROY_BLOCK
        || action == DiggingAction.STOP_DESTROY_BLOCK) {
        metaOf(userOf(event.getPlayer())).sentBreak = true;
      }
    } finally {
      reader.release();
    }
  }

  @PacketSubscription(
    priority = LOWEST,
    packetsIn = {FLYING, LOOK, POSITION, POSITION_LOOK, CLIENT_TICK_END},
    ignoreCancelled = false
  )
  public void tick(ProtocolPacketEvent event) {
    User user = userOf(event.getPlayer());
    PacketTypeCommon type = event.getPacketType();
    boolean modernBoundary = user.meta().protocol().sendsClientTickEnd();
    boolean boundary = modernBoundary ? PacketTypes.isClientEndTick(type) : !PacketTypes.isClientEndTick(type);
    if (!boundary) {
      return;
    }

    Meta meta = metaOf(user);
    if (meta.sentBreak && !meta.sentAnimation) {
      meta.buffer += 1.0;
      if (meta.buffer >= 2.0) {
        Violation violation = Violation.builderFor(BreakSpeedLimiter.class)
          .forPlayer(user.player())
          .withCheckName("FastBreak")
          .withMessage("broke blocks without swinging")
          .withDetails("missing arm animation in client tick")
          .withVL(2.0)
          .build();
        Modules.violationProcessor().processViolation(violation);
        meta.buffer = 1.0;
      }
    } else if (meta.sentBreak) {
      meta.buffer = Math.max(0.0, meta.buffer - 0.35);
    } else {
      meta.buffer = Math.max(0.0, meta.buffer - 0.05);
    }

    meta.sentAnimation = false;
    meta.sentBreak = false;
  }

  public static final class Meta extends CheckCustomMetadata {
    private boolean sentAnimation;
    private boolean sentBreak;
    private double buffer;
  }
}

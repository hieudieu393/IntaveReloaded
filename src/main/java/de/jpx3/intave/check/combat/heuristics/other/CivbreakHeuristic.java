package de.jpx3.intave.check.combat.heuristics.other;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.reader.BlockDigReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.BLOCK_DIG;

public final class CivbreakHeuristic extends MetaCheckPart<Heuristics, CivbreakHeuristic.CivbreakMeta> {
  public CivbreakHeuristic(Heuristics parentCheck) {
    super(parentCheck, CivbreakMeta.class);
  }

  @PacketSubscription(packetsIn = BLOCK_DIG)
  public void receiveInteractionPacket(ProtocolPacketEvent event) {
    User user = userOf(event.getPlayer());
    CivbreakMeta meta = metaOf(user);
    BlockDigReader reader = PacketReaders.readerOf(event);
    DiggingAction action = reader.action();
    if (action == DiggingAction.START_DIGGING) {
      meta.isMining = true;
    }
    if (action == DiggingAction.FINISHED_DIGGING) {
      if (user.protocolVersion() < ProtocolMetadata.VER_1_14 && !meta.isMining) {
        event.setCancelled(true);
      }
      meta.isMining = false;
    }
  }

  public static final class CivbreakMeta extends CheckCustomMetadata {
    private boolean isMining;
  }
}

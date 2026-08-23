package de.jpx3.intave.check.combat.heuristics.other;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class PacketOrderSwingHeuristic extends ClassicHeuristic<PacketOrderSwingHeuristic.PacketOrderSwingHeuristicMeta> {
  private final IntavePlugin plugin;

  public PacketOrderSwingHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.SWING_ORDER, PacketOrderSwingHeuristicMeta.class);
    this.plugin = IntavePlugin.singletonInstance();
  }

  @PacketSubscription(
    packetsIn = {
      FLYING, POSITION, POSITION_LOOK, LOOK, ARM_ANIMATION
    }
  )
  public void receiveMovementPacket(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    PacketOrderSwingHeuristicMeta heuristicMeta = metaOf(player);
    heuristicMeta.swingTick = event.getPacketType() == PacketType.Play.Client.ANIMATION;
  }

  @PacketSubscription(
    packetsIn = {
      ATTACK_ENTITY, USE_ENTITY
    }
  )
  public void receiveUseEntity(
    User user, EntityUseReader reader
  ) {
    ProtocolMetadata protocol = user.meta().protocol();
    PacketOrderSwingHeuristicMeta heuristicMeta = metaOf(user);
    if (user.meta().abilities().ignoringMovementPackets()) {
      return;
    }
    if (reader.isAttackPacket() && protocol.emptyFlyingPacketsAreExplicitlySent() && !heuristicMeta.swingTick) {
      String description = "swing not correlated with attack (" + user.meta().protocol().versionString() + ")";
      flag(user.player(), description);
      //dmc11
      user.nerf(AttackNerfStrategy.DMG_LIGHT, "11");
    }
  }

  public static final class PacketOrderSwingHeuristicMeta extends CheckCustomMetadata {
    private boolean swingTick;
  }
}

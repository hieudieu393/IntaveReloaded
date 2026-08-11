package de.jpx3.intave.check.combat.heuristics.combatpatterns.accuracy;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AttackMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class AccuracyLongTermHeuristic extends ClassicHeuristic<AccuracyLongTermHeuristic.ClickAccuracyMeta> {
  public AccuracyLongTermHeuristic(Heuristics parentCheck) { super(parentCheck, HeuristicsClassicType.ATTACK_ACCURACY, ClickAccuracyMeta.class); }

  @PacketSubscription(packetsIn = {ATTACK_ENTITY, USE_ENTITY, ARM_ANIMATION})
  public void evaluateFightAccuracy(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    AttackMetadata attackData = user.meta().attack();
    ClickAccuracyMeta heuristicMeta = metaOf(user);
    PacketTypeCommon packetType = event.getPacketType();
    Entity entity = attackData.lastAttackedEntity();
    if (entity == null || !entity.moving(0.05) || entity.ticksAlive < 200) return;
    if (!attackData.recentlyAttacked(500) || attackData.recentlySwitchedEntity(1000)) return;
    if (packetType == PacketType.Play.Client.ANIMATION) {
      heuristicMeta.swings++;
      return;
    }
    boolean isAttack;
    try (EntityUseReader reader = PacketReaders.readerOf(event)) {
      isAttack = reader.isAttackPacket();
    }
    if (!isAttack) return;
    heuristicMeta.attacks++;
    heuristicMeta.swings--;
    double failRate = (heuristicMeta.swings / heuristicMeta.attacks) * 100.0;
    if (heuristicMeta.attacks > 80) {
      if (failRate >= 0 && failRate < 3) {
        flag(player, "player maintains high attack accuracy (failRate: " + MathHelper.formatDouble(failRate, 2) + "%)");
      }
      heuristicMeta.attacks = 0;
      heuristicMeta.swings = 0;
    }
  }

  public static class ClickAccuracyMeta extends CheckCustomMetadata {
    public double attacks;
    public double swings;
  }
}

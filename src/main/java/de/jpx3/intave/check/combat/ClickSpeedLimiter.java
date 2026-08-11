/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.check.combat;

import com.github.retrooper.packetevents.event.CancellableEvent;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.MetaCheck;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.tracker.player.AbilityTracker;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.module.violation.ViolationContext;
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AbilityMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class ClickSpeedLimiter extends MetaCheck<ClickSpeedLimiter.ClickSpeedLimiterMeta> {
  private final IntavePlugin plugin;
  private final int maxCPS;

  public ClickSpeedLimiter(IntavePlugin plugin) {
    super("ClickSpeedLimiter", "clickspeedlimiter", ClickSpeedLimiterMeta.class);
    this.plugin = plugin;
    this.maxCPS = configuration().settings().intInBoundsBy("max-cps", 8, 40, 20);
  }

  @PacketSubscription(priority = ListenerPriority.HIGH, packetsIn = {ATTACK_ENTITY, USE_ENTITY})
  public void attackEntity(User user, EntityUseReader reader, CancellableEvent cancellable) {
    ClickSpeedLimiterMeta meta = metaOf(user);
    if (reader.isAttackPacket()) {
      if (user.protocolVersion() <= ProtocolMetadata.VER_1_8) meta.attackCountArray[meta.attackArrayIndex]++;
      else meta.attacksDuringFlyingPackets.add(System.currentTimeMillis());
    }
    if ((System.currentTimeMillis() - meta.lastFlag) / 1000d < 1d) cancellable.setCancelled(true);
  }

  @PacketSubscription(priority = ListenerPriority.HIGH, packetsIn = {FLYING, LOOK, POSITION, POSITION_LOOK})
  public void clientTickUpdate(ProtocolPacketEvent event) {
    Player player = (Player) event.getPlayer();
    User user = userOf(player);
    ClickSpeedLimiterMeta meta = metaOf(user);
    PacketTypeCommon pt = event.getPacketType();
    AbilityMetadata abilities = user.meta().abilities();
    if (abilities.inGameModeIncludePending(AbilityTracker.GameMode.SPECTATOR)) return;

    if (user.protocolVersion() <= ProtocolMetadata.VER_1_8) {
      meta.countAccuratePositionPackets = 20;
    } else if (meta.lastMovePacketType != null) {
      SimulationEnvironment movementData = user.meta().movement();
      if (movementData.receivedFlyingPacketIn(0)
        || meta.lastMovePacketType == PacketType.Play.Client.PLAYER_FLYING
        || meta.lastMovePacketType == PacketType.Play.Client.PLAYER_ROTATION) {
        meta.countAccuratePositionPackets = 0;
        long now = System.currentTimeMillis();
        long timeDiff = now - meta.lastTickTimeStamp;
        int ticks = (int) (timeDiff / 50f);
        int newIndex = meta.attackArrayIndex + ticks;
        while (newIndex > 19) newIndex -= 20;
        while (newIndex < 0) newIndex += 20;
        meta.attackArrayIndex = newIndex;

        for (int i = 1; i <= ticks; i++) {
          int index = meta.attackArrayIndex - i;
          while (index > 19) index -= 20;
          while (index < 0) index += 20;
          meta.attackCountArray[index] = 0;
        }
        for (long timeStampFromAttack : meta.attacksDuringFlyingPackets) {
          timeDiff = now - timeStampFromAttack;
          ticks = (int) (timeDiff / 50f);
          if (ticks < 20) {
            int index = meta.attackArrayIndex - ticks;
            while (index > 19) index -= 20;
            while (index < 0) index += 20;
            meta.attackCountArray[index]++;
          }
        }
      } else {
        meta.attackCountArray[meta.attackArrayIndex] = meta.attacksDuringFlyingPackets.size();
        meta.countAccuratePositionPackets++;
      }
    }

    int sum = 0;
    for (int attacks : meta.attackCountArray) sum += attacks;
    if (sum > maxCPS) {
      int addedVL = meta.countAccuratePositionPackets > 20 ? 3 : 1;
      Violation violation = Violation.builderFor(ClickSpeedLimiter.class)
        .forPlayer(player).withMessage("attacked too quickly").withDetails(sum + " c/s").withVL(addedVL).build();
      ViolationContext violationContext = Modules.violationProcessor().processViolation(violation);
      if (violationContext.shouldCounterThreat()) meta.lastFlag = System.currentTimeMillis();
    }
    prepareNextTick(meta, pt);
  }

  private void prepareNextTick(ClickSpeedLimiterMeta meta, PacketTypeCommon pt) {
    meta.attacksDuringFlyingPackets.clear();
    meta.lastMovePacketType = pt;
    meta.attackArrayIndex++;
    if (meta.attackArrayIndex > 19) meta.attackArrayIndex = 0;
    meta.attackCountArray[meta.attackArrayIndex] = 0;
    meta.lastTickTimeStamp = System.currentTimeMillis();
  }

  public static final class ClickSpeedLimiterMeta extends CheckCustomMetadata {
    private long lastFlag;
    PacketTypeCommon lastMovePacketType;
    List<Long> attacksDuringFlyingPackets = new ArrayList<>();
    int[] attackCountArray = new int[20];
    int attackArrayIndex;
    long lastTickTimeStamp = System.currentTimeMillis();
    int countAccuratePositionPackets;
  }
}

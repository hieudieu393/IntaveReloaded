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

package de.jpx3.intave.check.movement.timer;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.annotate.DispatchTarget;
import de.jpx3.intave.check.CheckStatistics;
import de.jpx3.intave.check.CheckViolationLevelDecrementer;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.movement.Timer;
import de.jpx3.intave.cleanup.GarbageCollector;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.feedback.FeedbackOptions;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.module.tracker.player.AbilityTracker;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.module.violation.ViolationContext;
import de.jpx3.intave.packet.PacketSender;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.TELEPORT;
import static de.jpx3.intave.math.MathHelper.formatDouble;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.LOGIN;

public class PlayerTime extends MetaCheckPart<Timer, PlayerTime.PlayerTimeMeta> {
  private static final long DEFAULT_DELAY = 500;
  private static final long DEFAULT_THRESHOLD = 10;
  private final Map<UUID, Long> playerJoinTimeCache = GarbageCollector.watch(new HashMap<>());
  private final CheckViolationLevelDecrementer decrementer;

  public PlayerTime(Timer parentCheck) {
    super(parentCheck, PlayerTimeMeta.class);
    decrementer = parentCheck.decrementer();

    Bukkit.getScheduler().runTaskTimer(IntavePlugin.singletonInstance(), () -> {
      UserRepository.applyOnAll(user -> {
        ConnectionMetadata connectionData = user.meta().connection();
        PlayerTimeMeta checkMeta = metaOf(user);
        Modules.feedback().synchronize(user.player(), System.nanoTime(), (unused, time) -> {
          connectionData.queueToNextTransaction(() -> {
            checkMeta.time = Math.max(checkMeta.time, checkMeta.limitToBeApplied);
            checkMeta.queuedLimit = time;
          });
        });
      });
    }, 0, 1);
  }

  @PacketSubscription(priority = ListenerPriority.HIGHEST, packetsOut = LOGIN)
  public void receiveLogin(ProtocolPacketEvent event) {
    Player player = (Player) event.getPlayer();
    if (player == null) return;
    User user = userOf(player);
    PlayerTimeMeta checkMeta = metaOf(user);
    playerJoinTimeCache.put(player.getUniqueId(), System.nanoTime());
    PacketSender.sendServerPacketWithoutEvent(player, event.getFullBufferClone());
    user.tickFeedback(() -> checkMeta.gameJoinReceived = true);
    event.setCancelled(true);
  }

  @BukkitEventSubscription
  public void on(PlayerJoinEvent join) {
    Player player = join.getPlayer();
    if (!playerJoinTimeCache.containsKey(player.getUniqueId())) {
      User user = userOf(player);
      PlayerTimeMeta checkMeta = metaOf(user);
      playerJoinTimeCache.put(player.getUniqueId(), System.nanoTime());
      user.tickFeedback(() -> checkMeta.gameJoinReceived = true);
    }
  }

  @BukkitEventSubscription public void on(PlayerQuitEvent quit) { playerJoinTimeCache.remove(quit.getPlayer().getUniqueId()); }

  @DispatchTarget
  public void receiveMovement(ProtocolPacketEvent event) {
    Player player = (Player) event.getPlayer();
    if (player == null) return;
    User user = userOf(player);
    PlayerTimeMeta checkMeta = metaOf(user);
    MetadataBundle bundle = user.meta();
    MovementMetadata movementData = bundle.movement();
    AbilityMetadata abilityData = user.meta().abilities();
    if (checkMeta.time == -1) checkMeta.time = playerJoinTimeCache.getOrDefault(player.getUniqueId(), System.nanoTime());
    if (!checkMeta.gameJoinReceived || movementData.ticksPast(TELEPORT) == 0
      || abilityData.inGameModeIncludePending(AbilityTracker.GameMode.CREATIVE)
      || abilityData.ignoringMovementPackets() || movementData.isInVehicle()) return;

    checkMeta.time += 50_000_000;
    checkMeta.limitToBeApplied = checkMeta.queuedLimit;
    long diff = checkMeta.time - System.nanoTime();
    statisticApply(user, CheckStatistics::increaseTotal);
    int limit = 40_000_000;
    if (diff > limit && !movementData.isInVehicle()) {
      double displayValue = Math.max(0.01, diff / (50 * 1_000_000f));
      Violation violation = Violation.builderFor(Timer.class).forPlayer(player)
        .withMessage("moved too frequently")
        .withDetails(formatDouble(displayValue, 2) + " ticks ahead")
        .withVL(Math.min(Math.max(displayValue * 3f, 1), 5)).build();
      ViolationContext violationContext = Modules.violationProcessor().processViolation(violation);
      if (violationContext.shouldCounterThreat()) {
        checkMeta.lastTimerFlag = System.currentTimeMillis();
        movementData.invalidMovement = true;
        statisticApply(user, CheckStatistics::increaseFails);
        Modules.mitigate().movement().emulationSetBack(player, Motion.newEmpty(), 3, 2, false);
        if (violationContext.violationLevelAfter() > 50) user.nerfPermanently(AttackNerfStrategy.DMG_HIGH, "timer");
      }
      checkMeta.time -= 10_000_000;
      return;
    } else if (System.currentTimeMillis() - checkMeta.lastTimerFlag > 10000) {
      decrementer.decrement(user, 0.005);
    }

    int blinkLimit = parentCheck().blinkLimit();
    if (!checkMeta.inTeleport && !user.justJoined() && !user.meta().abilities().probablyFlying()
      && blinkLimit > 0 && diff < -Math.abs(blinkLimit * 50_000_000L)) {
      checkMeta.inTeleport = true;
      user.tickFeedback(() -> checkMeta.inTeleport = false, FeedbackOptions.SELF_SYNCHRONIZATION);
      double displayValue = -diff / (50 * 1_000_000f);
      Violation violation = Violation.builderFor(Timer.class).forPlayer(player)
        .withMessage("is halting game ticks")
        .withDetails(formatDouble(displayValue, 2) + " ticks behind").withVL(0).build();
      Modules.violationProcessor().processViolation(violation);
      Modules.mitigate().movement().emulationSetBack(player, Motion.newEmpty(), 1, 2, false);
    }
    statisticApply(user, CheckStatistics::increasePasses);
  }

  @BukkitEventSubscription public void receiveItemConsume(PlayerItemConsumeEvent event) { cancelOnPacketOverflow(event.getPlayer(), event); }
  @BukkitEventSubscription public void receiveBowShoot(EntityShootBowEvent event) { if (event.getEntity() instanceof Player) cancelOnPacketOverflow((Player) event.getEntity(), event); }
  @BukkitEventSubscription public void receiveHealthUpdate(EntityRegainHealthEvent event) { if (event.getEntity() instanceof Player) cancelOnPacketOverflow((Player) event.getEntity(), event); }
  @BukkitEventSubscription public void receiveAttackUpdate(EntityDamageByEntityEvent event) {
    Entity entity = event.getDamager();
    if (entity instanceof Player && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
      Player player = (Player) entity;
      cancelOnPacketOverflow(player, event, trustFactorSetting("act", player), trustFactorSetting("acl", player));
      if (System.currentTimeMillis() - metaOf(userOf(player)).lastTimerFlag < 600) event.setCancelled(true);
    }
  }

  private void cancelOnPacketOverflow(Player player, Cancellable cancellable) { cancelOnPacketOverflow(player, cancellable, DEFAULT_THRESHOLD, DEFAULT_DELAY); }
  private void cancelOnPacketOverflow(Player player, Cancellable cancellable, long threshold, long delay) {
    User user = userOf(player);
    PlayerTimeMeta timerData = metaOf(user);
    if (violationLevelOf(user) > threshold && System.currentTimeMillis() - timerData.lastTimerFlag < delay) {
      cancellable.setCancelled(true);
      player.updateInventory();
    }
  }

  private double violationLevelOf(User user) {
    ViolationMetadata violationLevelData = user.meta().violationLevel();
    Map<String, Map<String, Double>> violationLevel = violationLevelData.violationLevel;
    String name = name().toLowerCase();
    if (!violationLevel.containsKey(name)) return 0;
    return violationLevel.get(name).get("thresholds");
  }

  public static class PlayerTimeMeta extends CheckCustomMetadata {
    public long time = -1;
    public long queuedLimit = System.nanoTime();
    public long limitToBeApplied = System.nanoTime();
    public boolean gameJoinReceived;
    public long lastTimerFlag;
    public boolean inTeleport;
  }
}

/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 */
package de.jpx3.intave.check.combat.heuristics.combatpatterns;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientHeldItemChange;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.*;
import de.jpx3.intave.world.raytrace.Raytrace;
import de.jpx3.intave.world.raytrace.Raytracing;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.TELEPORT;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_8;

public final class AttackRequiredHeuristic extends ClassicHeuristic<AttackRequiredHeuristic.AttackRequiredMeta> {
  public AttackRequiredHeuristic(Heuristics parentCheck) { super(parentCheck, HeuristicsClassicType.ATTACK_REQUIRED, AttackRequiredMeta.class); }

  @PacketSubscription(priority = ListenerPriority.HIGH, packetsIn = ARM_ANIMATION)
  public void receiveSwing(ProtocolPacketEvent event) { metaOf((Player) event.getPlayer()).didSwing = true; }

  @PacketSubscription(priority = ListenerPriority.HIGH, packetsIn = {ATTACK_ENTITY, USE_ENTITY})
  public void receiveAttack(Player player, EntityUseReader reader) { if (reader.isAttackPacket()) metaOf(player).didAttack = true; }

  @PacketSubscription(priority = ListenerPriority.HIGH, packetsIn = HELD_ITEM_SLOT_IN)
  public void receiveSlotSwitch(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    int slot = new WrapperPlayClientHeldItemChange((PacketReceiveEvent) event).getSlot();
    if (slot < 0 || slot >= player.getInventory().getSize()) return;
    ItemStack item = player.getInventory().getItem(slot);
    if (item != null) metaOf(player).ticksSinceFishingRodItemSwitch = 0;
  }

  @PacketSubscription(priority = ListenerPriority.NORMAL, packetsIn = {FLYING, LOOK, POSITION, POSITION_LOOK, VEHICLE_MOVE})
  public void receiveMovement(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    ProtocolMetadata clientData = user.meta().protocol();
    AttackMetadata attackData = user.meta().attack();
    SimulationEnvironment movementData = user.meta().movement();
    Entity entity = attackData.lastAttackedEntity();
    if (entity == null || !entity.clientSynchronized || movementData.ticksPast(TELEPORT) < 5) return;
    if (clientData.protocolVersion() != VER_1_8 || clientData.outdatedClient()) return;
    if (entity.fakeDead || entity.dead) return;
    AttackRequiredMeta meta = metaOf(player);
    boolean recentlyUsedRot = meta.ticksSinceFishingRodItemSwitch < 5;
    if (!recentlyUsedRot && meta.didSwing && !meta.didAttack && cursorUponEntity(user, entity)) {
      long timeToLastFlag = System.currentTimeMillis() - meta.lastFlag;
      if (timeToLastFlag < 20_000 && timeToLastFlag > 1500) {
        int vl = (meta.vl += 200) / 200;
        if (vl >= 2) flag(player, "missed attack packet vl:" + vl);
      }
      meta.lastFlag = System.currentTimeMillis();
    }
    if (meta.didSwing && meta.didAttack && meta.vl > 0) meta.vl--;
    meta.ticksSinceFishingRodItemSwitch++;
    meta.expectedAttack = false;
    meta.didAttack = false;
    meta.didSwing = false;
  }

  private boolean cursorUponEntity(User user, Entity entity) {
    Player player = user.player();
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    ProtocolMetadata clientData = meta.protocol();
    float expandHitbox = 0.05f;
    double blockReachDistance = reachDistance(player.getGameMode() == GameMode.CREATIVE);
    float lastRotationYaw = movementData.lastRotationYaw % 360;
    float rotationYaw = movementData.rotationYaw % 360;
    boolean alternativePositionY = clientData.protocolVersion() == VER_1_8;
    boolean hasAlwaysMouseDelayFix = clientData.protocolVersion() >= 314;
    Raytrace distanceOfResult = Raytracing.blockConstraintEntityRaytrace(player, entity, alternativePositionY, movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ, rotationYaw, movementData.rotationPitch, expandHitbox);
    if (distanceOfResult.reach() > blockReachDistance) return false;
    if (!hasAlwaysMouseDelayFix) {
      distanceOfResult = Raytracing.blockConstraintEntityRaytrace(player, entity, true, movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ, lastRotationYaw, movementData.rotationPitch, expandHitbox);
    }
    return distanceOfResult.reach() <= blockReachDistance;
  }

  private float reachDistance(boolean creative) { return (creative ? 5.0F : 3.0F) - 0.005f; }

  public static final class AttackRequiredMeta extends CheckCustomMetadata {
    public boolean expectedAttack;
    public boolean didAttack, didSwing;
    public long lastFlag;
    public int vl;
    public int ticksSinceFishingRodItemSwitch;
  }
}

package de.jpx3.intave.check.combat.heuristics.modern;

import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.module.tracker.entity.EntityTracker;
import de.jpx3.intave.packet.PacketTypes;
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import de.jpx3.intave.world.raytrace.Raytrace;
import de.jpx3.intave.world.raytrace.Raytracing;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.TELEPORT;
import static de.jpx3.intave.module.linker.packet.ListenerPriority.LOW;
import static de.jpx3.intave.module.linker.packet.ListenerPriority.NORMAL;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

/**
 * Complements AttackRaytrace with obstruction awareness. Reach and hitbox validity remain owned by
 * AttackRaytrace; this part distinguishes a target hidden behind a block and a target selected
 * through a closer living entity. Both detections use a per-target buffer before adding VL.
 */
public final class CombatObstructionHeuristic extends ModernCombatHeuristic<CombatObstructionHeuristic.Meta> {
  private static final int MAX_PENDING_TARGETS = 10;
  private static final double SAFE_EXPANSION = 0.13D;
  private static final double MAX_DEFERRED_TARGET_DELTA = 1.0D;

  public CombatObstructionHeuristic(Heuristics parentCheck) {
    super(parentCheck, Meta.class);
  }

  @PacketSubscription(priority = LOW, packetsIn = {ATTACK_ENTITY, USE_ENTITY}, ignoreCancelled = false)
  public void receiveAttackPacket(ProtocolPacketEvent event) {
    EntityUseReader reader = PacketReaders.readerOf(event);
    try {
      if (!reader.isAttackPacket()) {
        return;
      }
      User user = userOf(event.getPlayer());
      Meta meta = metaOf(user);
      if (meta.pendingTargets.size() >= MAX_PENDING_TARGETS) {
        return;
      }
      int entityId = reader.entityId();
      Entity target = EntityTracker.entityByIdentifier(user, entityId);
      MovementMetadata movement = user.meta().movement();
      if (target != null && target.position != null) {
        meta.pendingTargets.add(new PendingTarget(
          target, target.position.clone(), entityId,
          movement.positionX, movement.positionY, movement.positionZ,
          movement.rotationYaw, movement.lastRotationYaw, movement.rotationPitch
        ));
      }
    } finally {
      reader.release();
    }
  }

  @PacketSubscription(priority = NORMAL, packetsIn = {FLYING, LOOK, POSITION, POSITION_LOOK, CLIENT_TICK_END})
  public void receiveMovementPacket(ProtocolPacketEvent event) {
    User user = userOf(event.getPlayer());
    ProtocolMetadata protocol = user.meta().protocol();
    PacketTypeCommon packetType = event.getPacketType();
    boolean clientTickEnd = PacketTypes.isClientEndTick(packetType);
    if (protocol.sendsClientTickEnd() && !clientTickEnd) {
      return;
    }

    Meta meta = metaOf(user);
    if (meta.pendingTargets.isEmpty()) {
      return;
    }

    MovementMetadata movement = user.meta().movement();
    if (movement.ticksPast(TELEPORT) <= 1 || movement.awaitTeleport || movement.isInVehicle()) {
      meta.pendingTargets.clear();
      return;
    }

    PendingTarget[] targets = meta.pendingTargets.toArray(new PendingTarget[0]);
    meta.pendingTargets.clear();
    for (PendingTarget target : targets) {
      evaluateTarget(user, target, meta);
    }
  }

  private void evaluateTarget(User user, PendingTarget pending, Meta meta) {
    Entity target = EntityTracker.entityByIdentifier(user, pending.entityId);
    if (target != pending.entity || target instanceof Entity.Destroyed || !target.hasTypeData()
      || !target.isEntityAlive() || !target.clientSynchronized
      || movedTooFar(target, pending.targetPosition)) {
      decay(meta.wallBuffers, pending.entityId);
      decay(meta.pierceBuffers, pending.entityId);
      return;
    }

    ProtocolMetadata protocol = user.meta().protocol();
    double x = pending.x;
    double y = pending.y;
    double z = pending.z;
    float yaw = pending.yaw % 360.0F;
    float lastYaw = pending.lastYaw % 360.0F;
    float pitch = pending.pitch;
    boolean alternativeY = protocol.emptyFlyingPacketsAreExplicitlySent();
    double maxReach = Raytracing.reachDistanceOf(user);

    Raytrace constrained = Raytracing.doubleMDFBlockConstraintEntityRaytrace(
      user.player(), target, alternativeY,
      x, y, z, lastYaw, yaw, pitch,
      SAFE_EXPANSION, false
    );
    Raytrace unobstructed = Raytracing.blockIgnoringEntityRaytrace(
      user.player(), target, alternativeY,
      x, y, z, yaw, pitch,
      SAFE_EXPANSION
    );

    boolean targetCanBeAimedAt = unobstructed.reach() <= maxReach;
    boolean blockedByWorld = targetCanBeAimedAt && constrained.reach() > maxReach;
    if (blockedByWorld) {
      int buffer = increment(meta.wallBuffers, pending.entityId);
      if (buffer >= 2) {
        flag(user, "wall-hit",
          "target=" + target.entityName() + " reach=" + format(unobstructed.reach()),
          4.0);
        meta.wallBuffers.put(pending.entityId, 1);
      }
    } else {
      decay(meta.wallBuffers, pending.entityId);
    }

    if (!targetCanBeAimedAt || constrained.reach() > maxReach) {
      decay(meta.pierceBuffers, pending.entityId);
      return;
    }

    Entity blocker = findCloserLivingEntity(user, target, x, y, z, yaw, pitch, maxReach, unobstructed.reach());
    if (blocker != null) {
      int buffer = increment(meta.pierceBuffers, pending.entityId);
      if (buffer >= 2) {
        flag(user, "entity-pierce",
          "target=" + target.entityName() + " blocker=" + blocker.entityName()
            + " targetReach=" + format(unobstructed.reach()),
          5.0);
        meta.pierceBuffers.put(pending.entityId, 1);
      }
    } else {
      decay(meta.pierceBuffers, pending.entityId);
    }
  }

  private static boolean movedTooFar(Entity target, Entity.EntityPositionContext snapshot) {
    double dx = target.position.posX - snapshot.posX;
    double dy = target.position.posY - snapshot.posY;
    double dz = target.position.posZ - snapshot.posZ;
    return !isDeferredTargetPositionStable(dx, dy, dz);
  }

  static boolean isDeferredTargetPositionStable(double dx, double dy, double dz) {
    return dx * dx + dy * dy + dz * dz <= MAX_DEFERRED_TARGET_DELTA * MAX_DEFERRED_TARGET_DELTA;
  }

  private Entity findCloserLivingEntity(
    User user, Entity target,
    double x, double y, double z,
    float yaw, float pitch,
    double maxReach, double targetReach
  ) {
    Entity best = null;
    double bestReach = targetReach;
    for (Entity candidate : user.meta().connection().entities()) {
      if (candidate == null || candidate == target || candidate instanceof Entity.Destroyed
        || candidate.entityId() == target.entityId() || !candidate.hasTypeData()
        || !candidate.typeData().isLivingEntity() || !candidate.isEntityAlive()) {
        continue;
      }
      // Avoid raytracing distant tracked entities; only entities near the normal combat ray can matter.
      if (candidate.distance(x, y, z) > maxReach + 2.0D) {
        continue;
      }

      Raytrace raytrace = Raytracing.blockIgnoringEntityRaytrace(
        user.player(), candidate, false,
        x, y, z, yaw, pitch,
        SAFE_EXPANSION
      );
      double reach = raytrace.reach();
      if (reach <= maxReach && reach + 0.03D < bestReach) {
        bestReach = reach;
        best = candidate;
      }
    }
    return best;
  }

  private static int increment(Map<Integer, Integer> buffers, int entityId) {
    Integer current = buffers.get(entityId);
    int next = current == null ? 1 : current + 1;
    buffers.put(entityId, next);
    return next;
  }

  private static void decay(Map<Integer, Integer> buffers, int entityId) {
    Integer current = buffers.get(entityId);
    if (current == null) {
      return;
    }
    if (current <= 1) {
      buffers.remove(entityId);
    } else {
      buffers.put(entityId, current - 1);
    }
  }

  private static String format(double value) {
    return String.format(java.util.Locale.ROOT, "%.4f", value);
  }

  public static final class Meta extends CheckCustomMetadata {
    private final Set<PendingTarget> pendingTargets = new LinkedHashSet<>();
    private final Map<Integer, Integer> wallBuffers = new HashMap<>();
    private final Map<Integer, Integer> pierceBuffers = new HashMap<>();
  }

  private static final class PendingTarget {
    private final Entity entity;
    private final Entity.EntityPositionContext targetPosition;
    private final int entityId;
    private final double x, y, z;
    private final float yaw, lastYaw, pitch;

    private PendingTarget(Entity entity, Entity.EntityPositionContext targetPosition, int entityId,
                          double x, double y, double z, float yaw, float lastYaw, float pitch) {
      this.entity = entity;
      this.targetPosition = targetPosition;
      this.entityId = entityId;
      this.x = x;
      this.y = y;
      this.z = z;
      this.yaw = yaw;
      this.lastYaw = lastYaw;
      this.pitch = pitch;
    }
  }
}

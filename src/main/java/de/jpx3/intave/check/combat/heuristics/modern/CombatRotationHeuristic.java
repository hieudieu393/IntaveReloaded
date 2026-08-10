package de.jpx3.intave.check.combat.heuristics.modern;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.TELEPORT;
import static de.jpx3.intave.module.linker.packet.ListenerPriority.HIGH;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.ATTACK_ENTITY;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.LOOK;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION_LOOK;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.USE_ENTITY;

/**
 * Adds attack-correlated rotation analysis on top of Intave's classic rotation heuristics.
 * It tracks the mouse-step mode on both axes, modulo yaw snaps, duplicate look updates and
 * short attack snap-back patterns while keeping the existing Heuristics VL/mitigation pipeline.
 */
public final class CombatRotationHeuristic extends ModernCombatHeuristic<CombatRotationHeuristic.Meta> {
  private static final int HISTORY_SIZE = 8;
  private static final int MODE_WINDOW = 80;
  private static final int SIGNIFICANT_MODE_SAMPLES = 15;

  public CombatRotationHeuristic(Heuristics parentCheck) {
    super(parentCheck, Meta.class);
  }

  @PacketSubscription(priority = HIGH, packetsIn = {ATTACK_ENTITY, USE_ENTITY}, ignoreCancelled = false)
  public void receiveAttackPacket(PacketEvent event) {
    EntityUseReader reader = PacketReaders.readerOf(event.getPacket());
    try {
      if (!reader.isAttackPacket()) {
        return;
      }

      User user = userOf(event.getPlayer());
      MovementMetadata movement = user.meta().movement();
      Meta meta = metaOf(user);
      if (movement.ticksPast(TELEPORT) <= 2 || movement.isInVehicle() || meta.historyFilled < 2) {
        resetAttackWindow(meta);
        return;
      }

      int index = (meta.historyHead - 2 + HISTORY_SIZE) % HISTORY_SIZE;
      meta.preAttackYaw = meta.yawHistory[index];
      meta.pendingAttackTicks = 0;
      meta.sawAttackSpike = false;
      meta.attackSpike = 0.0f;
    } finally {
      reader.release();
    }
  }

  @PacketSubscription(priority = HIGH, packetsIn = {LOOK, POSITION_LOOK})
  public void receiveRotationPacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    MovementMetadata movement = user.meta().movement();
    Meta meta = metaOf(user);

    float currentYaw = movement.rotationYaw;
    float deltaYaw = wrapDegrees(currentYaw - movement.lastRotationYaw);
    float deltaPitch = movement.rotationPitch - movement.lastRotationPitch;
    float absYaw = Math.abs(deltaYaw);
    float absPitch = Math.abs(deltaPitch);

    updateMouseProcessor(meta, absYaw, absPitch);

    boolean exempt = movement.ticksPast(TELEPORT) <= 2 || movement.isInVehicle();
    if (exempt) {
      meta.exemptNext = true;
      meta.duplicateBuffer = 0;
      resetAttackWindow(meta);
      finishRotation(meta, currentYaw, absYaw);
      return;
    }
    if (meta.exemptNext) {
      meta.exemptNext = false;
      finishRotation(meta, currentYaw, absYaw);
      return;
    }

    boolean recentCombat = user.meta().attack().recentlyAttacked(750) || meta.pendingAttackTicks >= 0;

    // Large modulo/reset snap: a normal small turn immediately followed by a 320+ degree yaw delta.
    if (currentYaw < 360.0f && currentYaw > -360.0f && absYaw > 320.0f && meta.lastDeltaYaw < 30.0f) {
      flag(user, "rotation-modulo", "large yaw snap=" + format(absYaw) + " previous=" + format(meta.lastDeltaYaw), 4.0);
    }

    // Duplicate look updates are only considered during combat and need a short buffer. This avoids
    // treating an isolated redundant rotation packet as automation.
    if (recentCombat && absYaw == 0.0f && absPitch == 0.0f) {
      if (++meta.duplicateBuffer >= 2) {
        flag(user, "duplicate-look", "repeated identical combat rotations", 2.0);
        meta.duplicateBuffer = 1;
      }
    } else if (meta.duplicateBuffer > 0) {
      meta.duplicateBuffer--;
    }

    // Attack snap-back: remember the pre-attack yaw, observe a spike, then detect a rapid return
    // to essentially the same yaw within three rotation updates.
    if (meta.pendingAttackTicks >= 0) {
      meta.pendingAttackTicks++;
      if (!meta.sawAttackSpike && absYaw > 30.0f) {
        meta.sawAttackSpike = true;
        meta.attackSpike = absYaw;
      }

      if (meta.sawAttackSpike) {
        float returnDifference = Math.abs(wrapDegrees(currentYaw - meta.preAttackYaw));
        if (returnDifference < 3.0f) {
          String details = "spike=" + format(meta.attackSpike)
            + " return=" + format(returnDifference)
            + " window=" + meta.pendingAttackTicks
            + sensitivityDetails(meta);
          flag(user, "rotation-snap-back", details, 4.0);
          resetAttackWindow(meta);
        } else if (meta.pendingAttackTicks >= 3) {
          resetAttackWindow(meta);
        }
      } else if (meta.pendingAttackTicks >= 3) {
        resetAttackWindow(meta);
      }
    }

    finishRotation(meta, currentYaw, absYaw);
  }

  private static void updateMouseProcessor(Meta meta, float absYaw, float absPitch) {
    if (absYaw > 0.0f && absYaw < 5.0f && meta.lastSmallYaw > 0.0f) {
      double divisor = gcd(absYaw, meta.lastSmallYaw);
      if (divisor > 1.0E-6) {
        addModeSample(meta.yawDivisors, divisor);
      }
    }
    if (absYaw > 0.0f && absYaw < 5.0f) {
      meta.lastSmallYaw = absYaw;
    }

    if (absPitch > 0.0f && absPitch < 5.0f && meta.lastSmallPitch > 0.0f) {
      double divisor = gcd(absPitch, meta.lastSmallPitch);
      if (divisor > 1.0E-6) {
        addModeSample(meta.pitchDivisors, divisor);
      }
    }
    if (absPitch > 0.0f && absPitch < 5.0f) {
      meta.lastSmallPitch = absPitch;
    }

    Mode yawMode = modeOf(meta.yawDivisors);
    if (yawMode.count > SIGNIFICANT_MODE_SAMPLES) {
      meta.modeYaw = yawMode.value;
      meta.sensitivityYaw = sensitivityFromStep(yawMode.value);
      meta.deltaDotsYaw = yawMode.value == 0.0 ? 0.0 : absYaw / yawMode.value;
    }

    Mode pitchMode = modeOf(meta.pitchDivisors);
    if (pitchMode.count > SIGNIFICANT_MODE_SAMPLES) {
      meta.modePitch = pitchMode.value;
      meta.sensitivityPitch = sensitivityFromStep(pitchMode.value);
      meta.deltaDotsPitch = pitchMode.value == 0.0 ? 0.0 : absPitch / pitchMode.value;
    }
  }

  private static void addModeSample(ArrayDeque<Double> values, double value) {
    if (values.size() >= MODE_WINDOW) {
      values.removeFirst();
    }
    values.addLast(quantize(value));
  }

  private static Mode modeOf(ArrayDeque<Double> values) {
    if (values.size() <= SIGNIFICANT_MODE_SAMPLES) {
      return Mode.EMPTY;
    }
    Map<Double, Integer> counts = new HashMap<>();
    double bestValue = 0.0;
    int bestCount = 0;
    for (Double value : values) {
      Integer count = counts.get(value);
      int next = count == null ? 1 : count + 1;
      counts.put(value, next);
      if (next > bestCount) {
        bestCount = next;
        bestValue = value;
      }
    }
    return new Mode(bestValue, bestCount);
  }

  private static double quantize(double value) {
    return Math.rint(value * 1_000_000.0) / 1_000_000.0;
  }

  private static double gcd(double a, double b) {
    a = Math.abs(a);
    b = Math.abs(b);
    if (a < b) {
      double swap = a;
      a = b;
      b = swap;
    }
    int iterations = 0;
    while (b > 1.0E-7 && iterations++ < 40) {
      double remainder = a % b;
      a = b;
      b = remainder;
    }
    return a;
  }

  private static double sensitivityFromStep(double step) {
    double scaled = step / 0.15F / 8.0D;
    double cubeRoot = Math.cbrt(scaled);
    return (cubeRoot - 0.2F) / 0.6F;
  }

  private static String sensitivityDetails(Meta meta) {
    if (meta.modeYaw <= 0.0 && meta.modePitch <= 0.0) {
      return "";
    }
    return " sens=" + format(meta.sensitivityYaw) + "/" + format(meta.sensitivityPitch)
      + " dots=" + format(meta.deltaDotsYaw) + "/" + format(meta.deltaDotsPitch);
  }

  private static void finishRotation(Meta meta, float yaw, float absYaw) {
    meta.lastDeltaYaw = absYaw;
    meta.yawHistory[meta.historyHead] = yaw;
    meta.historyHead = (meta.historyHead + 1) % HISTORY_SIZE;
    if (meta.historyFilled < HISTORY_SIZE) {
      meta.historyFilled++;
    }
  }

  private static void resetAttackWindow(Meta meta) {
    meta.pendingAttackTicks = -1;
    meta.sawAttackSpike = false;
    meta.attackSpike = 0.0f;
  }

  private static float wrapDegrees(float value) {
    value %= 360.0f;
    if (value >= 180.0f) {
      value -= 360.0f;
    }
    if (value < -180.0f) {
      value += 360.0f;
    }
    return value;
  }

  private static String format(double value) {
    return String.format(java.util.Locale.ROOT, "%.3f", value);
  }

  private static final class Mode {
    private static final Mode EMPTY = new Mode(0.0, 0);
    private final double value;
    private final int count;

    private Mode(double value, int count) {
      this.value = value;
      this.count = count;
    }
  }

  public static final class Meta extends CheckCustomMetadata {
    private final float[] yawHistory = new float[HISTORY_SIZE];
    private final ArrayDeque<Double> yawDivisors = new ArrayDeque<>();
    private final ArrayDeque<Double> pitchDivisors = new ArrayDeque<>();
    private int historyHead;
    private int historyFilled;
    private int pendingAttackTicks = -1;
    private int duplicateBuffer;
    private boolean sawAttackSpike;
    private boolean exemptNext;
    private float preAttackYaw;
    private float attackSpike;
    private float lastDeltaYaw;
    private float lastSmallYaw;
    private float lastSmallPitch;
    private double modeYaw;
    private double modePitch;
    private double sensitivityYaw;
    private double sensitivityPitch;
    private double deltaDotsYaw;
    private double deltaDotsPitch;
  }
}

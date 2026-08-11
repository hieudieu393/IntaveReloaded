package de.jpx3.intave.check.world.breakspeedlimiter;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.world.BreakSpeedLimiter;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import org.bukkit.Material;

import static de.jpx3.intave.module.linker.packet.ListenerPriority.LOWEST;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.BLOCK_DIG;

/**
 * Rejects attempts to mine client-compensated air/liquid/non-targetable block states. This reads
 * Intave's block cache instead of Bukkit world state, so speculative block changes and latency are
 * handled consistently with InteractionRaytrace.
 */
public final class AirLiquidBreakCheck extends MetaCheckPart<BreakSpeedLimiter, AirLiquidBreakCheck.Meta> {
  public AirLiquidBreakCheck(BreakSpeedLimiter parentCheck) {
    super(parentCheck, Meta.class);
  }

  @PacketSubscription(priority = LOWEST, packetsIn = BLOCK_DIG, ignoreCancelled = false)
  public void receive(ProtocolPacketEvent event) {
    if (!(event.delegate() instanceof PacketReceiveEvent)) {
      return;
    }
    User user = userOf(event.getPlayer());
    WrapperPlayClientPlayerDigging dig = new WrapperPlayClientPlayerDigging((PacketReceiveEvent) event.delegate());
    DiggingAction action = dig.getAction();
    if (action != DiggingAction.START_DIGGING && action != DiggingAction.FINISHED_DIGGING) {
      return;
    }

    Vector3i pos = dig.getBlockPosition();
    if (pos == null) {
      return;
    }

    Material type;
    boolean emptyOutline;
    try {
      type = user.blockCache().typeAt(pos.x, pos.y, pos.z);
      emptyOutline = user.blockCache().outlineShapeAt(pos.x, pos.y, pos.z).isEmpty();
    } catch (RuntimeException ignored) {
      return;
    }
    if (type == null) {
      return;
    }

    Meta meta = metaOf(user);
    boolean kelpLikeDoubleBreak = same(meta.lastPos, pos)
      && !meta.lastFlagged
      && meta.lastType != null
      && safeBlastLike(meta.lastType)
      && isWater(type);
    if (kelpLikeDoubleBreak) {
      remember(meta, pos, type, false);
      return;
    }

    boolean invalid = isAir(type)
      || isWater(type)
      || isLava(type)
      || "BUBBLE_COLUMN".equals(type.name())
      || "MOVING_PISTON".equals(type.name());

    // Empty outlines are normally not directly mineable. Keep a small allowlist for modern
    // targetable blocks whose interaction shape may be represented outside the outline cache.
    if (!invalid && emptyOutline && !allowEmptyOutline(type)) {
      invalid = true;
    }

    // Material#getHardness is unavailable on the legacy compile API used by this project. The
    // runtime floor is 1.21+, so use the stable names of vanilla -1-hardness blocks instead.
    if (!invalid && action == DiggingAction.FINISHED_DIGGING && isKnownUnbreakable(type)) {
      invalid = true;
    }

    if (!invalid) {
      meta.buffer = Math.max(0.0D, meta.buffer - 0.25D);
      remember(meta, pos, type, false);
      return;
    }

    meta.buffer += 1.0D;
    if (meta.buffer >= 1.5D) {
      Violation violation = Violation.builderFor(BreakSpeedLimiter.class)
        .forPlayer(user.player())
        .withCheckName("FastBreak")
        .withMessage("tried to break an invalid block state")
        .withDetails("type=" + type.name() + ", action=" + action + ", pos=" + compact(pos))
        .withVL(4.0D)
        .build();
      Modules.violationProcessor().processViolation(violation);
      meta.buffer = 0.75D;
      remember(meta, pos, type, true);
    } else {
      remember(meta, pos, type, false);
    }
  }

  private static boolean allowEmptyOutline(Material type) {
    String name = type.name();
    return name.endsWith("_SIGN")
      || name.endsWith("_WALL_SIGN")
      || name.contains("TRIPWIRE")
      || name.equals("REDSTONE_WIRE")
      || name.equals("STRUCTURE_VOID")
      || name.equals("LIGHT");
  }

  private static boolean isAir(Material type) {
    String name = type.name();
    return "AIR".equals(name) || "CAVE_AIR".equals(name) || "VOID_AIR".equals(name);
  }

  private static boolean isWater(Material type) {
    return type == Material.WATER || "WATER".equals(type.name()) || "STATIONARY_WATER".equals(type.name());
  }

  private static boolean isLava(Material type) {
    return type == Material.LAVA || "LAVA".equals(type.name()) || "STATIONARY_LAVA".equals(type.name());
  }

  private static boolean isKnownUnbreakable(Material type) {
    String name = type.name();
    return "BEDROCK".equals(name)
      || "BARRIER".equals(name)
      || "END_PORTAL".equals(name)
      || "END_PORTAL_FRAME".equals(name)
      || "COMMAND_BLOCK".equals(name)
      || "CHAIN_COMMAND_BLOCK".equals(name)
      || "REPEATING_COMMAND_BLOCK".equals(name)
      || "STRUCTURE_BLOCK".equals(name)
      || "JIGSAW".equals(name);
  }

  // Narrow exemption for the vanilla kelp/aquatic double-break edge case.
  private static boolean safeBlastLike(Material type) {
    String name = type.name();
    return name.contains("KELP")
      || name.contains("SEAGRASS")
      || name.contains("CORAL")
      || name.contains("FUNGUS")
      || name.contains("ROOTS");
  }

  private static void remember(Meta meta, Vector3i pos, Material type, boolean flagged) {
    meta.lastPos = new Vector3i(pos.x, pos.y, pos.z);
    meta.lastType = type;
    meta.lastFlagged = flagged;
  }

  private static boolean same(Vector3i a, Vector3i b) {
    return a != null && b != null && a.x == b.x && a.y == b.y && a.z == b.z;
  }

  private static String compact(Vector3i pos) {
    return pos.x + "/" + pos.y + "/" + pos.z;
  }

  public static final class Meta extends CheckCustomMetadata {
    private Vector3i lastPos;
    private Material lastType;
    private boolean lastFlagged;
    private double buffer;
  }
}

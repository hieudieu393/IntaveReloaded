package de.jpx3.intave.check.other.protocolscanner;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.other.ProtocolScanner;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.PacketTypes;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.packet.reader.PlayerMoveReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;

import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.TELEPORT;
import static de.jpx3.intave.module.linker.packet.ListenerPriority.LOWEST;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

/**
 * Detects abuse of the Mojang duplicate POSITION+LOOK behaviour present on 1.17-1.20 clients.
 *
 * A single duplicate is vanilla behaviour and may legitimately carry a rotation, so this guard
 * never cancels it. Only a sustained run above the vanilla pattern is reported as BadPackets.
 */
public final class DuplicateMovementGuard extends MetaCheckPart<ProtocolScanner, DuplicateMovementGuard.Meta> {
  private static final int MAX_DUPLICATES = 5;

  public DuplicateMovementGuard(ProtocolScanner parentCheck) {
    super(parentCheck, Meta.class);
  }

  @PacketSubscription(
    priority = LOWEST,
    packetsIn = {FLYING, LOOK, POSITION, POSITION_LOOK, CLIENT_TICK_END, VEHICLE_MOVE},
    ignoreCancelled = false
  )
  public void receive(PacketEvent event) {
    User user = userOf(event.getPlayer());
    Meta meta = metaOf(user);
    int protocol = user.protocolVersion();

    // Mojang's duplicate movement packet behaviour existed from 1.17 through 1.20.x and was
    // removed with the modern explicit tick boundary. Outside this window the signal is invalid.
    if (protocol < ProtocolMetadata.VER_1_17 || protocol >= ProtocolMetadata.VER_1_21) {
      reset(meta);
      return;
    }

    PacketType type = event.getPacketType();
    if (PacketTypes.isClientEndTick(type) || type == PacketType.Play.Client.VEHICLE_MOVE) {
      meta.duplicates = 0;
      return;
    }

    PlayerMoveReader reader = PacketReaders.readerOf(event.getPacket());
    try {
      MovementMetadata movement = user.meta().movement();
      boolean teleport = movement.awaitTeleport
        || movement.expectTeleport
        || movement.ticksPast(TELEPORT) <= 2;

      if (teleport) {
        meta.duplicates = 0;
        if (reader.hasMovement()) {
          updateReal(meta, reader.positionX(), reader.positionY(), reader.positionZ(), reader.onGround());
        }
        return;
      }

      // A duplicate is specifically a position+rotation packet. LOOK-only and empty flying packets
      // are normal tick traffic and terminate a duplicate run.
      if (!reader.hasMovement() || !reader.hasRotation()) {
        meta.duplicates = 0;
        if (reader.hasMovement()) {
          updateReal(meta, reader.positionX(), reader.positionY(), reader.positionZ(), reader.onGround());
        }
        return;
      }

      double x = reader.positionX();
      double y = reader.positionY();
      double z = reader.positionZ();
      boolean onGround = reader.onGround();
      if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
        // InvalidNumericData owns non-finite movement. Do not double-charge it here.
        reset(meta);
        return;
      }

      if (!meta.initialized) {
        updateReal(meta, x, y, z, onGround);
        return;
      }

      double threshold = protocol >= ProtocolMetadata.VER_1_18_2 ? 0.0002D : 0.03D;
      double dx = x - meta.lastRealX;
      double dy = y - meta.lastRealY;
      double dz = z - meta.lastRealZ;
      boolean insideMovementThreshold = dx * dx + dy * dy + dz * dz <= threshold * threshold;
      boolean duplicate = onGround == meta.lastRealOnGround && insideMovementThreshold;

      if (!duplicate) {
        meta.duplicates = 0;
        meta.buffer = Math.max(0.0D, meta.buffer - 0.25D);
        updateReal(meta, x, y, z, onGround);
        return;
      }

      meta.duplicates++;
      if (meta.duplicates <= MAX_DUPLICATES) {
        return;
      }

      // Six consecutive Mojang-style duplicates without a real movement/tick boundary is already
      // well outside the legitimate pattern. A small buffer still prevents repeated alerts from a
      // single network burst while preserving this as a crash/spam hardening signal.
      meta.buffer += 1.0D;
      if (meta.buffer < 2.0D || System.currentTimeMillis() - meta.lastFlagAt < 500L) {
        return;
      }

      Violation violation = Violation.builderFor(ProtocolScanner.class)
        .forPlayer(user.player())
        .withCheckName("BadPackets")
        .withMessage("sent excessive duplicate movement packets")
        .withDetails("duplicates=" + meta.duplicates + ", protocol=" + protocol)
        .withVL(5.0D)
        .build();
      Modules.violationProcessor().processViolation(violation);
      meta.lastFlagAt = System.currentTimeMillis();
      meta.buffer = 1.0D;
    } finally {
      reader.release();
    }
  }

  private static void updateReal(Meta meta, double x, double y, double z, boolean onGround) {
    meta.initialized = true;
    meta.lastRealX = x;
    meta.lastRealY = y;
    meta.lastRealZ = z;
    meta.lastRealOnGround = onGround;
  }

  private static void reset(Meta meta) {
    meta.initialized = false;
    meta.duplicates = 0;
    meta.buffer = 0.0D;
  }

  public static final class Meta extends CheckCustomMetadata {
    private boolean initialized;
    private boolean lastRealOnGround;
    private double lastRealX;
    private double lastRealY;
    private double lastRealZ;
    private int duplicates;
    private double buffer;
    private long lastFlagAt;
  }
}

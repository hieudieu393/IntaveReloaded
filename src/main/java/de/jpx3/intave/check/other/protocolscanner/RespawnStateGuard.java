package de.jpx3.intave.check.other.protocolscanner;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClientStatus;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.other.ProtocolScanner;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AbilityMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.CLIENT_COMMAND;

/** Experimental: detects repeated PERFORM_RESPAWN requests while the tracked player is alive. */
public final class RespawnStateGuard extends MetaCheckPart<ProtocolScanner, RespawnStateGuard.Meta> {
  public RespawnStateGuard(ProtocolScanner parentCheck) {
    super(parentCheck, Meta.class);
  }

  @PacketSubscription(packetsIn = CLIENT_COMMAND, ignoreCancelled = false)
  public void receive(ProtocolPacketEvent event) {
    if (!(event instanceof PacketReceiveEvent)) {
      return;
    }
    WrapperPlayClientClientStatus wrapper = new WrapperPlayClientClientStatus((PacketReceiveEvent) event);
    if (wrapper.getAction() != WrapperPlayClientClientStatus.Action.PERFORM_RESPAWN) {
      return;
    }

    User user = userOf(event.getPlayer());
    AbilityMetadata abilities = user.meta().abilities();
    MovementMetadata movement = user.meta().movement();
    Meta meta = metaOf(user);

    boolean deathTransition = movement.inRespawnScreen
      || abilities.health <= 0.0f
      || abilities.unsynchronizedHealth <= 0.0f;
    if (deathTransition || user.justJoined()) {
      meta.buffer = Math.max(0.0, meta.buffer - 0.75);
      return;
    }

    // This remains intentionally non-cancelling. A plugin can create unusual death-menu flows;
    // repeated impossible requests are useful evidence, but should not block recovery from them.
    meta.buffer += 1.0;
    if (meta.buffer < 2.0) {
      return;
    }

    Violation violation = Violation.builderFor(ProtocolScanner.class)
      .forPlayer(user.player())
      .withCheckName("BadPackets")
      .withMessage("requested respawn while alive")
      .withDetails("health=" + abilities.health + ", clientHealth=" + abilities.unsynchronizedHealth)
      .withVL(2.0)
      .build();
    Modules.violationProcessor().processViolation(violation);
    meta.buffer = 1.0;
  }

  public static final class Meta extends CheckCustomMetadata {
    private double buffer;
  }
}

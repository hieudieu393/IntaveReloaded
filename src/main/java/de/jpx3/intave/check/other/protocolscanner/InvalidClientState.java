package de.jpx3.intave.check.other.protocolscanner;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.check.CheckPart;
import de.jpx3.intave.check.other.ProtocolScanner;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.reader.AbilityInReader;
import de.jpx3.intave.user.User;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.ABILITIES_IN;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.SPECTATE;
import static de.jpx3.intave.module.tracker.player.AbilityTracker.GameMode.SPECTATOR;

/**
 * Rejects client state transitions that are impossible according to the server-confirmed ability
 * and game-mode state. Ability/game-mode tracking is transaction-aware elsewhere in Intave, so
 * these checks do not race legitimate server updates.
 */
public final class InvalidClientState extends CheckPart<ProtocolScanner> {
  public InvalidClientState(ProtocolScanner parentCheck) {
    super(parentCheck);
  }

  @PacketSubscription(
    ignoreCancelled = false,
    packetsIn = ABILITIES_IN
  )
  public void receiveAbilities(User user, AbilityInReader reader, ProtocolPacketEvent event) {
    if (!reader.requestedFlying() || user.meta().abilities().allowFlying()) {
      return;
    }

    cancelAndFlag(user, event, "claimed to be flying without flight permission", "flight=true, allowFlight=false");
  }

  @PacketSubscription(
    ignoreCancelled = false,
    packetsIn = SPECTATE
  )
  public void receiveSpectate(User user, ProtocolPacketEvent event) {
    if (user.meta().abilities().inGameModeIncludePending(SPECTATOR)) {
      return;
    }

    cancelAndFlag(user, event, "sent a spectate packet outside spectator mode", "gamemode is not spectator");
  }

  private void cancelAndFlag(User user, ProtocolPacketEvent event, String message, String details) {
    if (event.isReadOnly()) {
      event.setReadOnly(false);
    }
    event.setCancelled(true);

    int vl = parentCheck().configuration().settings().intBy("invalid-state-vl", 5);
    Violation violation = Violation.builderFor(ProtocolScanner.class)
      .forPlayer(user.player())
      .withMessage(message)
      .withDetails(details)
      .withVL(Math.max(1, vl))
      .build();
    Modules.violationProcessor().processViolation(violation);
  }
}

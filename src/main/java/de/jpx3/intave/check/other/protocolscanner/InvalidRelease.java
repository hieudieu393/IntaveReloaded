/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 */
package de.jpx3.intave.check.other.protocolscanner;

import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import de.jpx3.intave.check.CheckPart;
import de.jpx3.intave.check.other.ProtocolScanner;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.reader.BlockDigReader;
import de.jpx3.intave.user.User;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.BLOCK_DIG;

public final class InvalidRelease extends CheckPart<ProtocolScanner> {
  public InvalidRelease(ProtocolScanner parentCheck) {
    super(parentCheck);
  }

  @PacketSubscription(packetsIn = BLOCK_DIG)
  public void checkValidateRelease(Player player, User user, BlockDigReader reader) {
    DiggingAction digType = reader.action();
    if (digType == null || user.protocolVersion() < 47 || digType != DiggingAction.RELEASE_USE_ITEM) {
      return;
    }
    int faceId = reader.faceId();
    if (faceId != BlockFace.DOWN.getFaceValue()) {
      Violation violation = Violation.builderFor(ProtocolScanner.class)
        .forPlayer(player)
        .withMessage("sent invalid release")
        .withDetails("face " + faceId)
        .withVL(3)
        .build();
      Modules.violationProcessor().processViolation(violation);
      user.meta().inventory().lastFoodConsumptionBlockRequest = System.currentTimeMillis();
    }
  }
}

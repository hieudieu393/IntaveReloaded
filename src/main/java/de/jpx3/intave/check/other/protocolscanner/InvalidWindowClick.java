package de.jpx3.intave.check.other.protocolscanner;

import de.jpx3.intave.check.CheckPart;
import de.jpx3.intave.check.other.ProtocolScanner;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.reader.WindowClickReader;
import de.jpx3.intave.user.User;
import com.github.retrooper.packetevents.event.CancellableEvent;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.WINDOW_CLICK;

/** Validates impossible click-type/button combinations without assuming a specific container menu. */
public final class InvalidWindowClick extends CheckPart<ProtocolScanner> {
  public InvalidWindowClick(ProtocolScanner parentCheck) {
    super(parentCheck);
  }

  @PacketSubscription(packetsIn = WINDOW_CLICK, ignoreCancelled = false)
  public void receive(User user, WindowClickReader reader, CancellableEvent cancellable) {
    WindowClickReader.InventoryClickType type = reader.clickType();
    int button = reader.button();
    if (type == null) {
      return;
    }

    boolean invalid;
    switch (type) {
      case PICKUP:
      case QUICK_MOVE:
      case CLONE:
        invalid = button < 0 || button > 2;
        break;
      case SWAP:
        invalid = (button < 0 || button > 8) && button != 40;
        break;
      case THROW:
        invalid = button != 0 && button != 1;
        break;
      case QUICK_CRAFT:
        invalid = button < 0 || button > 10 || button == 3 || button == 7;
        break;
      case PICKUP_ALL:
        invalid = button != 0;
        break;
      default:
        invalid = false;
    }

    if (!invalid) {
      return;
    }

    cancellable.setCancelled(true);
    Violation violation = Violation.builderFor(ProtocolScanner.class)
      .forPlayer(user.player())
      .withCheckName("BadPackets")
      .withMessage("sent an invalid inventory click")
      .withDetails("type=" + type + ", button=" + button + ", slot=" + reader.slot())
      .withVL(8.0)
      .build();
    Modules.violationProcessor().processViolation(violation);
  }
}

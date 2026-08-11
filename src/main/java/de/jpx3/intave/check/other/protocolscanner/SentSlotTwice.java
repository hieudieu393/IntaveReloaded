package de.jpx3.intave.check.other.protocolscanner;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.other.ProtocolScanner;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.HELD_ITEM_SLOT_IN;

public final class SentSlotTwice extends MetaCheckPart<ProtocolScanner, SentSlotTwice.SentSlotTwiceMeta> {
  private final int duplicateVl;
  private final int invalidSlotVl;

  public SentSlotTwice(ProtocolScanner parentCheck) {
    super(parentCheck, SentSlotTwiceMeta.class);
    this.duplicateVl = parentCheck.configuration().settings().intBy(
      "sst-vl",
      parentCheck.configuration().settings().intBy("check_sent_slot_twice_vl", 100)
    );
    this.invalidSlotVl = parentCheck.configuration().settings().intBy("invalid-slot-vl", 100);
  }

  @PacketSubscription(
    packetsIn = {
      HELD_ITEM_SLOT_IN
    }
  )
  public void receiveSlotSwitch(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    User user = userOf(player);
    SentSlotTwiceMeta meta = metaOf(user);
    int slot = packet.getIntegers().read(0);

    // Vanilla hotbar indices are always 0..8. Reject impossible values before trackers/server
    // code can consume them.
    if (slot < 0 || slot > 8) {
      if (event.isReadOnly()) {
        event.setReadOnly(false);
      }
      event.setCancelled(true);

      if (invalidSlotVl > 0) {
        Violation violation = Violation.builderFor(ProtocolScanner.class)
          .forPlayer(player)
          .withMessage("sent an out-of-bounds held slot")
          .withDetails("slot " + slot)
          .withVL(invalidSlotVl)
          .build();
        Modules.violationProcessor().processViolation(violation);
      }
      return;
    }

    // Slot zero is a normal hotbar slot and must be treated like every other slot here.
    if (meta.initialized && meta.lastSlot == slot) {
      if (duplicateVl > 0) {
        Violation violation = Violation.builderFor(ProtocolScanner.class)
          .forPlayer(player)
          .withMessage("sent slot twice")
          .withDetails("slot " + slot)
          .withVL(meta.slotPacketsSent > 4 ? duplicateVl : 0)
          .build();
        Modules.violationProcessor().processViolation(violation);
      }
    }

    meta.initialized = true;
    meta.lastSlot = slot;
    meta.slotPacketsSent++;
  }

  @Override
  public boolean enabled() {
    return super.enabled() && (duplicateVl != 0 || invalidSlotVl != 0);
  }

  public static final class SentSlotTwiceMeta extends CheckCustomMetadata {
    public boolean initialized;
    public int lastSlot;
    public int slotPacketsSent;
  }
}

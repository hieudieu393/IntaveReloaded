package de.jpx3.intave.check.other.protocolscanner;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEditBook;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.CheckPart;
import de.jpx3.intave.check.other.ProtocolScanner;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.user.User;

import java.util.List;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.B_EDIT;

/** Modern (1.17.1+) book-edit sanity checks using PacketEvents' typed wrapper. */
public final class BookEditGuard extends CheckPart<ProtocolScanner> {
  private static final int MAX_HOTBAR_SLOT = 8;

  public BookEditGuard(ProtocolScanner parentCheck) {
    super(parentCheck);
  }

  @PacketSubscription(packetsIn = B_EDIT, ignoreCancelled = false)
  public void receive(PacketEvent event) {
    if (!(event.delegate() instanceof PacketReceiveEvent)) {
      return;
    }

    WrapperPlayClientEditBook wrapper = new WrapperPlayClientEditBook((PacketReceiveEvent) event.delegate());
    boolean modernLimits = PacketEvents.getAPI().getServerManager().getVersion()
      .isNewerThanOrEquals(ServerVersion.V_1_21_2);
    int maxPages = modernLimits ? 100 : 200;
    int maxPageChars = modernLimits ? 1024 : 8192;
    int maxTitleChars = modernLimits ? 32 : 128;

    int slot = wrapper.getSlot();
    List<String> pages = wrapper.getPages();
    String title = wrapper.getTitle();

    String reason = null;
    if (slot < 0 || slot > MAX_HOTBAR_SLOT) {
      reason = "slot=" + slot;
    } else if (pages == null) {
      reason = "missing pages";
    } else if (pages.size() > maxPages) {
      reason = "pages=" + pages.size() + "/" + maxPages;
    } else {
      for (int i = 0; i < pages.size(); i++) {
        String page = pages.get(i);
        if (page == null || page.length() > maxPageChars) {
          reason = "page=" + i + " length=" + (page == null ? -1 : page.length()) + "/" + maxPageChars;
          break;
        }
      }
    }
    if (reason == null && title != null && title.length() > maxTitleChars) {
      reason = "title=" + title.length() + "/" + maxTitleChars;
    }
    if (reason == null) {
      return;
    }

    event.setCancelled(true);
    User user = userOf(event.getPlayer());
    Violation violation = Violation.builderFor(ProtocolScanner.class)
      .forPlayer(user.player())
      .withCheckName("BadPackets")
      .withMessage("sent an invalid book edit")
      .withDetails(reason)
      .withVL(20.0)
      .build();
    Modules.violationProcessor().processViolation(violation);
  }
}

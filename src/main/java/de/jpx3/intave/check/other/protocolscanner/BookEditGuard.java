package de.jpx3.intave.check.other.protocolscanner;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEditBook;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.CheckPart;
import de.jpx3.intave.check.other.ProtocolScanner;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.InventoryMetadata;
import org.bukkit.Material;

import java.util.List;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.B_EDIT;

/**
 * Modern book-edit sanity checks using PacketEvents' typed wrapper. Intave only supports 1.17+
 * clients, so the modern writable-book limits can be enforced directly.
 */
public final class BookEditGuard extends CheckPart<ProtocolScanner> {
  private static final int OFFHAND_SLOT = 40;
  private static final int MAX_HOTBAR_SLOT = 8;
  private static final int MAX_PAGES = 100;
  private static final int MAX_PAGE_CHARS = 1023;
  private static final int MAX_TITLE_CHARS = 15;

  public BookEditGuard(ProtocolScanner parentCheck) {
    super(parentCheck);
  }

  @PacketSubscription(packetsIn = B_EDIT, ignoreCancelled = false)
  public void receive(PacketEvent event) {
    if (!(event.delegate() instanceof PacketReceiveEvent)) {
      return;
    }

    User user = userOf(event.getPlayer());
    WrapperPlayClientEditBook wrapper = new WrapperPlayClientEditBook((PacketReceiveEvent) event.delegate());
    int slot = wrapper.getSlot();
    List<String> pages = wrapper.getPages();
    String title = wrapper.getTitle();

    String reason = validateSlotAndBook(user, slot);
    if (reason == null && pages == null) {
      reason = "missing pages";
    } else if (reason == null && pages.size() > MAX_PAGES) {
      reason = "pages=" + pages.size() + "/" + MAX_PAGES;
    } else if (reason == null) {
      for (int i = 0; i < pages.size(); i++) {
        String page = pages.get(i);
        if (page == null || page.length() > MAX_PAGE_CHARS) {
          reason = "page=" + i + " length=" + (page == null ? -1 : page.length()) + "/" + MAX_PAGE_CHARS;
          break;
        }
      }
    }

    // Modern vanilla never appends a second-or-later empty page to an edit packet. Keeping the
    // single empty first page valid avoids rejecting a newly opened blank book.
    if (reason == null && pages.size() > 1 && pages.get(pages.size() - 1).isEmpty()) {
      reason = "empty last page";
    }

    if (reason == null && title != null) {
      if (title.length() > MAX_TITLE_CHARS) {
        reason = "title=" + title.length() + "/" + MAX_TITLE_CHARS;
      } else if (title.isEmpty() || !title.trim().equals(title) || containsInvalidCharacters(title)) {
        reason = "invalid title";
      }
    }

    if (reason == null) {
      return;
    }

    if (event.isReadOnly()) {
      event.setReadOnly(false);
    }
    event.setCancelled(true);
    Violation violation = Violation.builderFor(ProtocolScanner.class)
      .forPlayer(user.player())
      .withCheckName("BadPackets")
      .withMessage("sent an invalid book edit")
      .withDetails(reason)
      .withVL(20.0)
      .build();
    Modules.violationProcessor().processViolation(violation);
  }

  private static String validateSlotAndBook(User user, int slot) {
    InventoryMetadata inventory = user.meta().inventory();
    if ((slot < 0 || slot > MAX_HOTBAR_SLOT) && slot != OFFHAND_SLOT) {
      return "slot=" + slot;
    }

    if (slot != OFFHAND_SLOT && slot != inventory.handSlot()) {
      return "slot=" + slot + ", selected=" + inventory.handSlot();
    }

    // The project intentionally compiles against legacy Bukkit APIs for compatibility, where the
    // writable-book enum constant is BOOK_AND_QUILL. Compare by material name so modern Paper's
    // WRITABLE_BOOK and legacy compile APIs are both supported without a hard enum reference.
    try {
      Material type = slot == OFFHAND_SLOT ? inventory.offhandItemType() : inventory.heldItemType();
      String typeName = type == null ? "" : type.name();
      if (!"WRITABLE_BOOK".equals(typeName) && !"BOOK_AND_QUILL".equals(typeName)) {
        return "not editing writable book, item=" + type;
      }
    } catch (Throwable ignored) {
      return null;
    }
    return null;
  }

  private static boolean containsInvalidCharacters(String string) {
    for (int i = 0; i < string.length(); i++) {
      char character = string.charAt(i);
      if (character == 167 || character < 32 || character == 127) {
        return true;
      }
    }
    return false;
  }
}

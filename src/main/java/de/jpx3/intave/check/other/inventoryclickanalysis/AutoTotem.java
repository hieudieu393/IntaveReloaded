package de.jpx3.intave.check.other.inventoryclickanalysis;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.block.type.MaterialSearch;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.other.InventoryClickAnalysis;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.reader.BlockDigReader;
import de.jpx3.intave.packet.reader.EntityStatusReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.packet.reader.WindowClickReader;
import de.jpx3.intave.packet.reader.WindowClickReader.InventoryClickType;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayDeque;
import java.util.Deque;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.ENTITY_STATUS;
import static de.jpx3.intave.packet.reader.WindowClickReader.InventoryClickType.PICKUP;

public final class AutoTotem extends MetaCheckPart<InventoryClickAnalysis, AutoTotem.AutoTotemMeta> {
  private static final int OFFHAND_SLOT = 45;
  private static final byte TOTEM_POP_STATUS = 35;
  private static final long FAST_REEQUIP_MS = 100L;
  private static final int MAX_INTERVAL_SAMPLES = 10;
  private static final Material TOTEM_OF_UNDYING = MaterialSearch.materialThatIsNamed("TOTEM_OF_UNDYING");

  public AutoTotem(InventoryClickAnalysis parentCheck) {
    super(parentCheck, AutoTotemMeta.class);
  }

  @PacketSubscription(
    packetsOut = {ENTITY_STATUS},
    ignoreCancelled = false
  )
  public void receiveEntityStatus(PacketEvent event) {
    User user = userOf(event.getPlayer());
    EntityStatusReader reader = PacketReaders.readerOf(event.getPacket());
    try {
      Byte status = reader.status();
      if (status == null || status != TOTEM_POP_STATUS || !reader.targetEntityIdIsSameAs(user)) {
        return;
      }

      AutoTotemMeta meta = metaOf(user);
      long generation = ++meta.popGeneration;
      meta.popConfirmed = false;
      meta.popConfirmedAt = 0L;
      meta.flyingsSincePop = 0;
      meta.swapsSincePop = 0;
      meta.timingSampleRecorded = false;

      user.tickFeedback(() -> {
        AutoTotemMeta current = metaOf(user);
        if (current.popGeneration != generation) {
          return;
        }
        current.popConfirmed = true;
        current.popConfirmedAt = System.currentTimeMillis();
        current.flyingsSincePop = 0;
        current.swapsSincePop = 0;
        current.timingSampleRecorded = false;
      });
    } finally {
      reader.release();
    }
  }

  @PacketSubscription(
    packetsIn = {WINDOW_CLICK}
  )
  public void receiveWindowClick(
    User user, WindowClickReader reader, Cancellable cancellable
  ) {
    Player player = user.player();
    int slot = reader.slot();
    InventoryClickType type = reader.clickType();

    boolean directOffhandSwap = type == InventoryClickType.SWAP && reader.button() == 40;
    boolean touchesOffhand = slot == OFFHAND_SLOT;
    if (directOffhandSwap || touchesOffhand) {
      handlePopAwareSwap(user);
    }

    if (type != PICKUP) {
      return;
    }
    String item = reader.clickedItemTypeIfPossible(player);
    if ("TOTEM_OF_UNDYING".equalsIgnoreCase(item) || (slot != OFFHAND_SLOT && metaOf(user).vl > 4)) {
      AutoTotemMeta meta = metaOf(user);
      meta.pickupClick = System.currentTimeMillis();
    } else if (slot == OFFHAND_SLOT) {
      AutoTotemMeta meta = metaOf(user);
      if (meta.pickupClick > 0) {
        long timeSincePickup = System.currentTimeMillis() - meta.pickupClick;
        if (meta.locked) {
          meta.sus |= timeSincePickup < 100;
          cancellable.setCancelled(true);
          return;
        }
        if (timeSincePickup < 100) {
          meta.sus = true;
          Violation violation = Violation.builderFor(InventoryClickAnalysis.class)
            .forPlayer(player)
            .withCheckName("AutoTotem")
            .withMessage("might be using auto-totem")
            .withDetails(timeSincePickup + "ms delay")
            .withVL(meta.vl).build();
          Modules.violationProcessor().processViolation(violation);
          Synchronizer.synchronizeDelayed(() -> {
            PlayerInventory inventory = user.player().getInventory();
            int freeSlot = inventory.firstEmpty();
            if (freeSlot >= 0) {
              ItemStack totem = inventory.getItemInOffHand();
              inventory.setItemInOffHand(null);
              inventory.setItem(freeSlot, totem);
              meta.pickupClick = System.currentTimeMillis();
              meta.sus = false;
              meta.locked = true;
              user.refreshSprintState(x -> {
                Synchronizer.synchronizeDelayed(() -> {
                  PlayerInventory inventory2 = user.player().getInventory();
                  if (!meta.sus) {
                    inventory2.setItem(OFFHAND_SLOT, totem);
                    inventory2.setItem(freeSlot, null);
                    meta.vl = 4;
                  } else {
                    meta.vl *= 2;
                  }
                  meta.locked = false;
                }, 8);
              });
            }
          }, 2);
        }
      }
    }
  }

  @PacketSubscription(
    packetsIn = {BLOCK_DIG},
    ignoreCancelled = false
  )
  public void receiveDig(PacketEvent event) {
    BlockDigReader reader = PacketReaders.readerOf(event.getPacket());
    try {
      if (reader.action() != null && "SWAP_ITEM_WITH_OFFHAND".equals(reader.action().name())) {
        handlePopAwareSwap(userOf(event.getPlayer()));
      }
    } finally {
      reader.release();
    }
  }

  @PacketSubscription(
    packetsIn = {FLYING, LOOK, POSITION, POSITION_LOOK, CLIENT_TICK_END},
    ignoreCancelled = false
  )
  public void receiveMovement(PacketEvent event) {
    AutoTotemMeta meta = metaOf(userOf(event.getPlayer()));
    if (!meta.popConfirmed) {
      return;
    }
    if (++meta.flyingsSincePop > 6) {
      resetPopWindow(meta);
    }
  }

  @PacketSubscription(
    packetsIn = {CLOSE_WINDOW},
    ignoreCancelled = false
  )
  public void receiveCloseWindow(PacketEvent event) {
    User user = userOf(event.getPlayer());
    AutoTotemMeta meta = metaOf(user);
    if (!meta.popConfirmed) {
      return;
    }

    if (meta.swapsSincePop > 0 && meta.flyingsSincePop == 0) {
      meta.sameTickBuffer += 1.0;
      if (meta.sameTickBuffer > 2.0) {
        flagPopAware(user, "pop + offhand swap + close-window in one client tick", 4);
        meta.sameTickBuffer = 1.0;
      }
    } else {
      meta.sameTickBuffer = Math.max(0.0, meta.sameTickBuffer - 0.5);
    }
    resetPopWindow(meta);
  }

  private void handlePopAwareSwap(User user) {
    AutoTotemMeta meta = metaOf(user);
    if (!meta.popConfirmed || meta.popConfirmedAt <= 0L) {
      return;
    }

    meta.swapsSincePop++;
    if (meta.timingSampleRecorded) {
      return;
    }
    meta.timingSampleRecorded = true;

    long delta = System.currentTimeMillis() - meta.popConfirmedAt;
    if (delta < 0 || delta > 5000) {
      resetPopWindow(meta);
      return;
    }

    if (meta.intervals.size() >= MAX_INTERVAL_SAMPLES) {
      meta.intervals.removeFirst();
    }
    meta.intervals.addLast(delta);

    if (delta < FAST_REEQUIP_MS) {
      meta.popTimingBuffer += 1.0;
      if (meta.popTimingBuffer > 1.5) {
        flagPopAware(user, "re-equipped offhand " + delta + "ms after confirmed totem pop", 4);
        meta.popTimingBuffer = 1.0;
      }
    } else {
      meta.popTimingBuffer = Math.max(0.0, meta.popTimingBuffer - 0.25);
    }

    if (meta.intervals.size() >= 4) {
      double mean = 0.0;
      for (Long interval : meta.intervals) {
        mean += interval;
      }
      mean /= meta.intervals.size();

      double variance = 0.0;
      for (Long interval : meta.intervals) {
        double diff = interval - mean;
        variance += diff * diff;
      }
      double standardDeviation = Math.sqrt(variance / meta.intervals.size());

      if (mean < 350.0 && standardDeviation < 35.0) {
        meta.consistencyBuffer += 1.0;
        if (meta.consistencyBuffer > 2.0) {
          flagPopAware(user,
            "machine-like totem timing mean=" + Math.round(mean) + "ms sd=" + Math.round(standardDeviation) + "ms", 3);
          meta.consistencyBuffer = 1.0;
        }
      } else {
        meta.consistencyBuffer = Math.max(0.0, meta.consistencyBuffer - 0.5);
      }
    }
  }

  private void flagPopAware(User user, String details, int vl) {
    Violation violation = Violation.builderFor(InventoryClickAnalysis.class)
      .forPlayer(user.player())
      .withCheckName("AutoTotem")
      .withMessage("might be using auto-totem")
      .withDetails(details)
      .withVL(vl)
      .build();
    Modules.violationProcessor().processViolation(violation);
  }

  private static void resetPopWindow(AutoTotemMeta meta) {
    meta.popConfirmed = false;
    meta.popConfirmedAt = 0L;
    meta.flyingsSincePop = 0;
    meta.swapsSincePop = 0;
    meta.timingSampleRecorded = false;
  }

  public static class AutoTotemMeta extends CheckCustomMetadata {
    private long pickupClick;
    private int vl = 4;
    private boolean sus;
    private boolean locked;

    private long popGeneration;
    private boolean popConfirmed;
    private long popConfirmedAt;
    private int flyingsSincePop;
    private int swapsSincePop;
    private boolean timingSampleRecorded;
    private double popTimingBuffer;
    private double consistencyBuffer;
    private double sameTickBuffer;
    private final Deque<Long> intervals = new ArrayDeque<>();
  }

  @Override
  public boolean enabled() {
    return super.enabled() && TOTEM_OF_UNDYING != null;
  }
}

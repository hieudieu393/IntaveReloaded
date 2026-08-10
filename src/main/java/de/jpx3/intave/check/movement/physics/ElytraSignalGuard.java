package de.jpx3.intave.check.movement.physics;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.MetaCheck;
import de.jpx3.intave.check.movement.Physics;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.PacketTypes;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import static de.jpx3.intave.module.linker.packet.ListenerPriority.LOWEST;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

/** Layered Elytra invariants that complement Physics' gliding simulator. */
public final class ElytraSignalGuard extends MetaCheck<ElytraSignalGuard.Meta> {
  public ElytraSignalGuard() {
    super("ElytraSignalGuard", "elytrasignalguard", Meta.class);
  }

  @Override
  public boolean enabled() { return true; }

  @Override
  public boolean performLinkage() { return true; }

  @PacketSubscription(priority = LOWEST, packetsIn = ENTITY_ACTION_IN, ignoreCancelled = false)
  public void action(PacketEvent event) {
    if (!(event.delegate() instanceof PacketReceiveEvent)) return;
    WrapperPlayClientEntityAction action = new WrapperPlayClientEntityAction((PacketReceiveEvent) event.delegate());
    if (action.getAction() != WrapperPlayClientEntityAction.Action.START_FLYING_WITH_ELYTRA) return;

    User user = userOf(event.getPlayer());
    MovementMetadata movement = user.meta().movement();
    Meta meta = metaOf(user);

    if (movement.gliding) {
      score(user, meta, 1.0D, "started gliding while already gliding", 2.0D);
    }

    if (meta.glideThisTick || meta.glideLastTick) {
      score(user, meta, tickingReliably(user) ? 1.0D : 0.5D,
        "started gliding in consecutive client ticks", 2.0D);
    }
    meta.glideThisTick = true;

    if (!wearingElytra(user)) {
      meta.noElytraBuffer += 1.0D;
      if (meta.noElytraBuffer >= 2.0D) {
        event.setCancelled(true);
        flag(user, "started gliding without an elytra", "chest item is not ELYTRA", 4.0D);
        meta.noElytraBuffer = 1.0D;
      }
    } else {
      meta.noElytraBuffer = Math.max(0.0D, meta.noElytraBuffer - 0.5D);
    }

    if (user.meta().protocol().sendsInputs()) {
      long sinceJump = System.currentTimeMillis() - movement.lastTimeJumped;
      if (!movement.clientPressedJump && sinceJump > 250L) {
        meta.jumpBuffer += 0.75D;
        if (meta.jumpBuffer >= 2.25D) {
          flag(user, "started gliding without a valid jump transition",
            "jump=false, sinceJump=" + sinceJump + "ms", 2.0D);
          meta.jumpBuffer = 1.0D;
        }
      } else {
        meta.jumpBuffer = Math.max(0.0D, meta.jumpBuffer - 0.35D);
      }
    }
  }

  @PacketSubscription(
    priority = LOWEST,
    packetsIn = {FLYING, LOOK, POSITION, POSITION_LOOK, CLIENT_TICK_END},
    ignoreCancelled = false
  )
  public void tick(PacketEvent event) {
    User user = userOf(event.getPlayer());
    MovementMetadata movement = user.meta().movement();
    Meta meta = metaOf(user);

    boolean modernBoundary = user.meta().protocol().sendsClientTickEnd();
    PacketType type = event.getPacketType();
    boolean boundary = modernBoundary ? PacketTypes.isClientEndTick(type) : !PacketTypes.isClientEndTick(type);
    if (!boundary) return;

    if (movement.gliding) {
      if (movement.isInVehicle()) {
        score(user, meta, 0.75D, "gliding while riding a vehicle", 2.0D);
      }
      if (movement.onGround && movement.lastOnGround) {
        meta.groundBuffer += 0.5D;
        if (meta.groundBuffer >= 2.0D) {
          flag(user, "remained gliding while grounded", "onGround for consecutive ticks", 1.5D);
          meta.groundBuffer = 1.0D;
        }
      } else {
        meta.groundBuffer = Math.max(0.0D, meta.groundBuffer - 0.25D);
      }
      if (!wearingElytra(user)) {
        meta.noElytraBuffer += 0.5D;
        if (meta.noElytraBuffer >= 2.5D) {
          flag(user, "continued gliding without an elytra", "tracked gliding=true", 3.0D);
          meta.noElytraBuffer = 1.25D;
        }
      }
    } else {
      meta.groundBuffer = Math.max(0.0D, meta.groundBuffer - 0.25D);
      meta.noElytraBuffer = Math.max(0.0D, meta.noElytraBuffer - 0.15D);
    }

    meta.glideLastTick = meta.glideThisTick;
    meta.glideThisTick = false;
    meta.generalBuffer = Math.max(0.0D, meta.generalBuffer - 0.05D);
  }

  private void score(User user, Meta meta, double amount, String details, double vl) {
    meta.generalBuffer += amount;
    if (meta.generalBuffer < 2.0D) return;
    flag(user, details, details, vl);
    meta.generalBuffer = 1.0D;
  }

  private void flag(User user, String message, String details, double vl) {
    Violation violation = Violation.builderFor(Physics.class)
      .forPlayer(user.player())
      .withCheckName("Elytra")
      .withMessage(message)
      .withDetails(details)
      .withVL(vl)
      .build();
    Modules.violationProcessor().processViolation(violation);
  }

  private static boolean wearingElytra(User user) {
    try {
      ItemStack chest = user.player().getInventory().getChestplate();
      return chest != null && chest.getType() == Material.ELYTRA;
    } catch (Throwable ignored) {
      // A non-main-thread Bukkit implementation may refuse inventory access. Do not turn that into
      // a detection; Physics still verifies the actual glide motion.
      return true;
    }
  }

  private static boolean tickingReliably(User user) {
    double average = user.meta().connection().averageMovementPacketTimestamp();
    return average > 0.0D && average <= 90.0D;
  }

  public static final class Meta extends CheckCustomMetadata {
    private boolean glideThisTick;
    private boolean glideLastTick;
    private double generalBuffer;
    private double noElytraBuffer;
    private double jumpBuffer;
    private double groundBuffer;
  }
}

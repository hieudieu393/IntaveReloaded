package de.jpx3.intave.check.combat.clickpatterns;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import de.jpx3.intave.check.Blueprint;
import de.jpx3.intave.check.combat.ClickPatterns;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.reader.BlockDigReader;
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import static com.github.retrooper.packetevents.protocol.player.DiggingAction.DROP_ITEM;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.violation.Violation.ViolationFlags.DISPLAY_IN_ALL_VERBOSE_MODES;

public abstract class TickAlignedHistoryBlueprint<E extends TickAlignedMeta> extends Blueprint<ClickPatterns, TickAlignedMeta, E> {
  private int historyLength = 80;

  public TickAlignedHistoryBlueprint(ClickPatterns parentCheck, Class<? extends E> metaClass) {
    super(parentCheck, metaClass);
  }

  @PacketSubscription(priority = ListenerPriority.HIGH, packetsIn = {USE_ENTITY, ARM_ANIMATION, BLOCK_DIG})
  public final void clientClickUpdate(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    TickAlignedMeta meta = metaOf(user);
    PacketTypeCommon type = event.getPacketType();
    if (type == PacketType.Play.Client.INTERACT_ENTITY) {
      EntityUseReader reader = PacketReaders.readerOf(event);
      try {
        if (reader.useAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) meta.attacks++;
      } finally {
        reader.release();
      }
    } else if (type == PacketType.Play.Client.ANIMATION) {
      meta.clicks++;
    } else if (type == PacketType.Play.Client.PLAYER_DIGGING) {
      BlockDigReader reader = PacketReaders.readerOf(event);
      try {
        DiggingAction digType = reader.action();
        if (!(digType == DROP_ITEM && user.meta().inventory().heldItemType() == Material.AIR)) {
          meta.breakingBlock = user.meta().attack().inBreakProcess;
          meta.places++;
        }
      } finally {
        reader.release();
      }
    }
  }

  @PacketSubscription(priority = ListenerPriority.HIGH, packetsIn = {FLYING, LOOK, POSITION, POSITION_LOOK})
  public final void clientTickUpdate(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    TickAlignedMeta meta = metaOf(user);
    TickAction action = TickAction.NOTHING;
    int intensity = 0;
    if (meta.clicks > 0) { action = TickAction.CLICK; intensity = meta.clicks; }
    if (meta.attacks > 0) { action = TickAction.ATTACK; intensity = meta.attacks; }
    else if (meta.places > 0) { action = TickAction.PLACE; intensity = meta.places; }
    append(user, action, intensity);
    meta.attacks = 0;
    meta.clicks = 0;
    meta.places = 0;
    meta.tickCount++;
    if (meta.tickCount % meta.historyLength == 0) analyzeClicks(user, metaOf(user));
  }

  public abstract void analyzeClicks(User user, E meta);

  public final void flag(User user, String message, int vl) {
    Violation violation = Violation.builderFor(ClickPatterns.class).forPlayer(user.player()).withDefaultThreshold()
      .withMessage(message).withDetails("pattern: " + buildHistoryString(user))
      .appendFlags(DISPLAY_IN_ALL_VERBOSE_MODES).withVL(vl).build();
    Modules.violationProcessor().processViolation(violation);
  }

  private String buildHistoryString(User user) {
    TickAlignedMeta meta = metaOf(user);
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < meta.historyLength; i++) {
      TickAction action = meta.tickActions.get(i);
      int intensity = meta.tickIntensity.get(i);
      builder.append(action.repChar());
      if (intensity > 1) builder.append("!").append(intensity);
    }
    int maxCps = 0;
    for (int i = 0; i < meta.historyLength - 20; i++) {
      int cps = 0;
      for (int j = i; j < i + 20; j++) {
        TickAction action = meta.tickActions.get(j);
        int intensity = meta.tickIntensity.get(j);
        if (action == TickAction.CLICK || action == TickAction.ATTACK) cps += intensity;
      }
      maxCps = Math.max(maxCps, cps);
    }
    builder.append(" up to ").append(maxCps).append("cps");
    return builder.toString();
  }

  public final void append(User user, TickAction action, int intensity) {
    TickAlignedMeta baseMeta = metaOf(user);
    if (action == TickAction.NOTHING) baseMeta.breakingBlock = false;
    baseMeta.inBlockBreak.remove(0);
    baseMeta.inBlockBreak.add(baseMeta.breakingBlock);
    baseMeta.tickActions.remove(0);
    baseMeta.tickActions.add(action);
    baseMeta.tickIntensity.remove(0);
    baseMeta.tickIntensity.add(intensity);
  }

  public enum TickAction {
    NOTHING('_'), CLICK('C'), ATTACK('A'), PLACE('P');
    private final char representation;
    TickAction(char representation) { this.representation = representation; }
    public char repChar() { return representation; }
  }
}

package de.jpx3.intave.module.actionbar;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.chat.ChatTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChatMessage;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSystemChatMessage;
import de.jpx3.intave.executor.TaskTracker;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.player.ActionBar;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Bukkit;

import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static de.jpx3.intave.module.linker.packet.PacketId.Server.CHAT_OUT;

public final class ActionBarDisplayer extends Module {
  private final ClickFeeder clickFeeder = new ClickFeeder();
  private final Lock lock = new ReentrantLock();

  @Override
  public void enable() {
    Modules.linker().bukkitEvents().registerEventsIn(clickFeeder);
    Modules.linker().packetEvents().linkSubscriptionsIn(clickFeeder);
  }

  @PacketSubscription(priority = ListenerPriority.HIGH, packetsOut = CHAT_OUT)
  public void clientClickUpdate(ProtocolPacketEvent event) {
    if (!(event instanceof PacketSendEvent)) return;
    User user = UserRepository.userOf(event.getPlayer());
    if (!inSubscription(user)) return;
    PacketSendEvent sendEvent = (PacketSendEvent) event;
    PacketTypeCommon type = event.getPacketType();
    boolean actionBar = type == PacketType.Play.Server.ACTION_BAR;
    if (type == PacketType.Play.Server.SYSTEM_CHAT_MESSAGE) {
      actionBar = new WrapperPlayServerSystemChatMessage(sendEvent).isOverlay();
    } else if (type == PacketType.Play.Server.CHAT_MESSAGE) {
      WrapperPlayServerChatMessage wrapper = new WrapperPlayServerChatMessage(sendEvent);
      actionBar = wrapper.getMessage() != null && wrapper.getMessage().getType() == ChatTypes.GAME_INFO;
    }
    if (actionBar) event.setCancelled(true);
  }

  public void subscribe(User receiver, User target, DisplayType type) {
    if (!receiver.hasPlayer() || !target.hasPlayer()) return;
    try {
      lock.lock();
      receiver.setActionTarget(target.id());
      target.addActionReceiver(receiver.id(), type);
      startTaskFor(receiver);
    } finally { lock.unlock(); }
  }

  public boolean inSubscription(User receiver) { return receiver.actionTarget() != null; }

  public void unsubscribe(User receiver) {
    try {
      lock.lock();
      UUID id = receiver.actionTarget();
      receiver.setActionTarget(null);
      User target = UserRepository.userOf(id);
      target.removeActionSubscription(receiver.id());
    } finally { lock.unlock(); }
  }

  private void startTaskFor(User receiver) {
    if (!receiver.hasPlayer()) return;
    UUID target = receiver.actionTarget();
    int[] counter = {0};
    int[] taskId = new int[1];
    taskId[0] = Bukkit.getScheduler().scheduleAsyncRepeatingTask(plugin, () -> {
      boolean cancelTask = counter[0]++ >= 20 * 60 * 15 || !receiver.hasPlayer() || !receiver.player().isOnline()
        || !inSubscription(receiver) || receiver.actionTarget() != target;
      User targetUser = UserRepository.userOf(target);
      cancelTask |= !targetUser.hasPlayer() || !targetUser.player().isOnline() || !targetUser.anyActionSubscriptions();
      if (cancelTask) {
        Bukkit.getScheduler().cancelTask(taskId[0]);
        TaskTracker.stopped(taskId[0]);
        unsubscribe(receiver);
        return;
      }
      String text = targetUser.actionDisplayOf(DisplayType.CLICKS);
      if (text != null) ActionBar.sendActionBar(receiver.player(), text);
    }, 1, 1);
    TaskTracker.begun(taskId[0]);
  }

  @Override
  public void disable() { }
}

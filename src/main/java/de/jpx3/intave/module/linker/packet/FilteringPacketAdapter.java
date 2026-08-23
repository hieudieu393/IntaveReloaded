package de.jpx3.intave.module.linker.packet;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.access.UnsupportedFallbackOperationException;
import de.jpx3.intave.diagnostic.timings.Timing;
import de.jpx3.intave.diagnostic.timings.Timings;
import de.jpx3.intave.module.linker.SubscriptionInstanceProvider;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Native PacketEvents subscription handler used by the forwarding listeners. */
final class FilteringPacketAdapter implements Comparable<FilteringPacketAdapter> {
  private final String methodName;
  private final ListenerPriority priority;
  private final SubscriptionInstanceProvider<User, ?, PacketEventSubscriber> subscriber;
  private final PacketSubscriptionMethodExecutor executor;
  private final Set<PacketTypeCommon> packetTypes;
  private final Map<PacketTypeCommon, Timing> localTimings = new ConcurrentHashMap<>();
  private final boolean ignoreCancelled;

  FilteringPacketAdapter(
    SubscriptionInstanceProvider<User, ?, PacketEventSubscriber> subscriber,
    ListenerPriority priority,
    PacketTypeCommon[] packetTypes,
    String methodName,
    PacketSubscriptionMethodExecutor executor,
    boolean ignoreCancelled
  ) {
    this.subscriber = subscriber;
    this.priority = priority;
    this.methodName = methodName;
    this.executor = executor;
    this.ignoreCancelled = ignoreCancelled;
    Set<PacketTypeCommon> identityTypes = Collections.newSetFromMap(new IdentityHashMap<>());
    Collections.addAll(identityTypes, packetTypes);
    this.packetTypes = Collections.unmodifiableSet(identityTypes);
  }

  boolean accepts(PacketTypeCommon packetType) {
    return packetTypes.contains(packetType);
  }

  void invoke(ProtocolPacketEvent event) {
    if (!accepts(event.getPacketType()) || !validateEvent(event)) {
      return;
    }

    PacketTypeCommon packetType = event.getPacketType();
    Timing timing = localTimings.computeIfAbsent(packetType, Timings::packetTimingOf);
    try {
      Timings.EXE_NETTY.start();
      timing.start();
      Player player = event.getPlayer();
      User user = UserRepository.userOf(player);
      boolean outbound = packetType.getSide() == com.github.retrooper.packetevents.protocol.PacketSide.SERVER;
      if (outbound ? user.shouldIgnoreNextOutboundPacket() : user.shouldIgnoreNextInboundPacket()) {
        return;
      }
      subscriber.apply(user, usr -> executor.invoke(usr, event));
    } catch (UnsupportedFallbackOperationException ignored) {
      // Some subscriptions intentionally have no fallback for temporary connection states.
    } catch (RuntimeException exception) {
      processFailure(event, exception);
    } catch (Error error) {
      processFailure(event, error);
      throw error;
    } finally {
      timing.stop();
      Timings.EXE_NETTY.stop();
    }
  }

  private boolean validateEvent(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    if (player == null) {
      return false;
    }
    if (!ignoreCancelled && event.isCancelled()) {
      return false;
    }
    return UserRepository.hasUser(player) || event.getPacketType() == PacketType.Play.Server.JOIN_GAME;
  }

  PacketEventSubscriber subscriber() {
    return subscriber.fallback();
  }

  ListenerPriority priority() {
    return priority;
  }

  @Override
  public int compareTo(FilteringPacketAdapter other) {
    return Integer.compare(priority.slot(), other.priority.slot());
  }

  private void processFailure(ProtocolPacketEvent event, Throwable throwable) {
    PacketTypeCommon packetType = event.getPacketType();
    String packetName = packetType == null ? "unknown" : String.valueOf(packetType);
    IntaveLogger.logger().error(
      "PacketEvents callback failed for " + packetName + " in "
        + subscriber.getClass().getSimpleName() + "." + methodName + ": "
        + throwable.getClass().getSimpleName() + ": " + String.valueOf(throwable.getMessage())
    );
  }
}

/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 */
package de.jpx3.intave.module.linker.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.CancellableEvent;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.klass.create.IRXClassFactory;
import de.jpx3.intave.library.asm.Type;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.OneForAll;
import de.jpx3.intave.module.linker.OneForOne;
import de.jpx3.intave.module.linker.SubscriptionInstanceProvider;
import de.jpx3.intave.packet.reader.PacketReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntUnaryOperator;

import static de.jpx3.intave.IntaveControl.IGNORE_CHUNK_PACKETS;

/** Links Intave packet subscriptions directly to PacketEvents. */
public final class PacketSubscriptionLinker extends Module {
  private static boolean IGNORE_CHAT_PACKETS;
  private static boolean IGNORE_SCOREBOARD_TEAM_PACKETS;

  private final IntavePlugin plugin;
  private final Map<PacketTypeCommon, SCOWAList<FilteringPacketAdapter>> internalMappings = new ConcurrentHashMap<>();
  private final List<PacketListenerAbstract> registeredListeners = new ArrayList<>();
  private final Set<String> exclusionNoted = ConcurrentHashMap.newKeySet();

  public PacketSubscriptionLinker(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public void enable() {
    boolean defaultIgnore = true;
    IGNORE_CHAT_PACKETS = plugin.getConfig().getBoolean("compatibility.ignore-scoreboard-packets", defaultIgnore);
    IGNORE_SCOREBOARD_TEAM_PACKETS = plugin.getConfig().getBoolean("compatibility.ignore-scoreboard-packets", defaultIgnore);
  }

  @Override
  public synchronized void disable() {
    unregisterAllListeners();
    internalMappings.values().forEach(SCOWAList::clear);
    internalMappings.clear();
    argumentPools.remove();
  }

  public void linkSubscriptionsIn(PacketEventSubscriber subscriber) {
    SubscriptionInstanceProvider<User, ?, PacketEventSubscriber> provider = instanceProviderFor(subscriber);
    for (Method method : provider.type().getMethods()) {
      if (methodRequestsSubscription(method)) {
        linkSubscription(provider, method);
      }
    }
  }

  public void removeSubscriptionsOf(PacketEventSubscriber subscriber) {
    Class<? extends PacketEventSubscriber> subscriberClass = subscriber.getClass();
    for (SCOWAList<FilteringPacketAdapter> handlers : internalMappings.values()) {
      handlers.removeIf(handler -> handler.subscriber() != null && handler.subscriber().getClass().equals(subscriberClass));
    }
  }

  public synchronized void refreshLinkages() {
    unregisterAllListeners();
    for (Map.Entry<PacketTypeCommon, SCOWAList<FilteringPacketAdapter>> entry : internalMappings.entrySet()) {
      if (!entry.getValue().isEmpty()) {
        registerListener(new ForwardingPacketAdapter(entry.getKey(), entry.getValue()));
      }
    }
  }

  private void unregisterAllListeners() {
    for (PacketListenerAbstract listener : registeredListeners) {
      PacketEvents.getAPI().getEventManager().unregisterListener(listener);
    }
    registeredListeners.clear();
  }

  private void registerListener(PacketListenerAbstract listener) {
    PacketEvents.getAPI().getEventManager().registerListener(listener);
    registeredListeners.add(listener);
  }

  private boolean methodRequestsSubscription(Method method) {
    return method.getAnnotation(PacketSubscription.class) != null && validParameters(method) && validModifiers(method);
  }

  private final Set<Class<?>> validParameterTypes = new HashSet<>(Arrays.asList(
    ProtocolPacketEvent.class,
    CancellableEvent.class,
    User.class,
    Player.class,
    PacketReader.class,
    PacketTypeCommon.class
  ));

  private boolean validParameters(Method method) {
    return Arrays.stream(method.getParameterTypes())
      .allMatch(type -> validParameterTypes.stream().anyMatch(valid -> valid.isAssignableFrom(type)));
  }

  private boolean validModifiers(Method method) {
    int modifiers = method.getModifiers();
    return !Modifier.isStatic(modifiers) && Modifier.isPublic(modifiers);
  }

  private void linkSubscription(
    SubscriptionInstanceProvider<User, ?, PacketEventSubscriber> provider,
    Method method
  ) {
    PacketSubscription metadata = method.getAnnotation(PacketSubscription.class);
    PacketTypeCommon[] packetTypes = translatedPacketTypes(metadata.packetsIn(), metadata.packetsOut(), metadata.debug());
    if (packetTypes.length == 0) {
      return;
    }

    PacketSubscriptionMethodExecutor executor = assembleSubscriptionMethodCaller(provider.type(), method);
    FilteringPacketAdapter handler = new FilteringPacketAdapter(
      provider,
      metadata.priority(),
      packetTypes,
      method.getName(),
      executor,
      metadata.ignoreCancelled()
    );

    if (metadata.prioritySlot() == PrioritySlot.EXTERNAL) {
      registerListener(new DirectPacketEventsListener(handler));
      return;
    }

    // Engine.INTERNAL used Intave's old injection layer. PacketEvents is now the sole packet engine.
    for (PacketTypeCommon packetType : packetTypes) {
      internalMappings.computeIfAbsent(packetType, ignored -> new SCOWAList<>()).add(handler);
    }
  }

  private SubscriptionInstanceProvider<User, ?, PacketEventSubscriber> instanceProviderFor(PacketEventSubscriber subscriber) {
    if (subscriber instanceof PlayerPacketEventSubscriber) {
      PlayerPacketEventSubscriber playerListener = (PlayerPacketEventSubscriber) subscriber;
      return new OneForOne<>(playerListener::packetSubscriberFor);
    }
    return new OneForAll<>(subscriber);
  }

  private PacketTypeCommon[] translatedPacketTypes(
    PacketId.Client[] inbound,
    PacketId.Server[] outbound,
    boolean debug
  ) {
    Set<PacketTypeCommon> types = Collections.newSetFromMap(new IdentityHashMap<>());
    Collections.addAll(types, PacketTypeResolver.client(inbound));
    Collections.addAll(types, PacketTypeResolver.server(outbound));
    types.removeIf(Objects::isNull);
    types.removeIf(this::excluded);
    if (debug) {
      IntaveLogger.logger().info("PacketEvents mapping: " + Arrays.toString(types.toArray()));
    }
    return types.toArray(new PacketTypeCommon[0]);
  }

  private boolean excluded(PacketTypeCommon packetType) {
    boolean tabChatPacket = packetType == PacketType.Play.Client.TAB_COMPLETE
      || packetType == PacketType.Play.Server.TAB_COMPLETE
      || packetType == PacketType.Play.Client.CHAT_MESSAGE;
    boolean excluded = (IGNORE_CHAT_PACKETS && tabChatPacket)
      || (IGNORE_CHUNK_PACKETS && packetType == PacketType.Play.Server.CHUNK_DATA)
      || (IGNORE_SCOREBOARD_TEAM_PACKETS && packetType == PacketType.Play.Server.TEAMS);
    if (excluded && exclusionNoted.add(packetType.getName())) {
      IntaveLogger.logger().info("Ignoring " + packetType.getName() + " packets");
    }
    return excluded;
  }

  private static final ThreadLocal<Map<Integer, ArrayDeque<Object[]>>> argumentPools =
    ThreadLocal.withInitial(HashMap::new);

  private PacketSubscriptionMethodExecutor assembleSubscriptionMethodCaller(
    Class<? extends PacketEventSubscriber> targetClass,
    Method calledMethod
  ) {
    if (calledMethod.getParameterCount() == 1 && calledMethod.getParameterTypes()[0] == ProtocolPacketEvent.class) {
      String packetSubscriberSuperClassPath = canonicalRepresentation(className(PacketEventSubscriber.class));
      String packetSubscriberClassPath = canonicalRepresentation(className(targetClass));
      String packetEventClassPath = canonicalRepresentation(className(ProtocolPacketEvent.class));
      Class<PacketSubscriptionMethodExecutor> executorClass = IRXClassFactory.assembleCallerClass(
        PacketSubscriptionLinker.class.getClassLoader(),
        PacketSubscriptionMethodExecutor.class,
        "<irx>",
        "invoke",
        "(L" + packetSubscriberSuperClassPath + ";L" + packetEventClassPath + ";)V",
        "(L" + packetSubscriberClassPath + ";L" + packetEventClassPath + ";)V",
        packetSubscriberClassPath,
        calledMethod.getName(),
        Type.getMethodDescriptor(calledMethod),
        false,
        false,
        IntUnaryOperator.identity()
      );
      return instanceOf(executorClass);
    }

    Class<?>[] parameterTypes = calledMethod.getParameterTypes();
    int length = parameterTypes.length;
    int playerIndex = findParameterPosition(parameterTypes, Player.class);
    int userIndex = findParameterPosition(parameterTypes, User.class);
    int cancellableIndex = findParameterPosition(parameterTypes, CancellableEvent.class);
    int readerIndex = findParameterPosition(parameterTypes, PacketReader.class);
    int eventIndex = findParameterPosition(parameterTypes, ProtocolPacketEvent.class);
    int typeIndex = findParameterPosition(parameterTypes, PacketTypeCommon.class);

    return (subscriber, event) -> {
      Map<Integer, ArrayDeque<Object[]>> pools = argumentPools.get();
      ArrayDeque<Object[]> pool = pools.computeIfAbsent(length, ignored -> new ArrayDeque<>());
      Object[] arguments = pool.pollFirst();
      if (arguments == null) {
        arguments = new Object[length];
      }
      PacketReader packetReader = null;
      try {
        Player player = event.getPlayer();
        if (playerIndex != -1) arguments[playerIndex] = player;
        if (userIndex != -1) arguments[userIndex] = UserRepository.userOf(player);
        if (cancellableIndex != -1) arguments[cancellableIndex] = event;
        if (eventIndex != -1) arguments[eventIndex] = event;
        if (typeIndex != -1) arguments[typeIndex] = event.getPacketType();
        if (readerIndex != -1) {
          packetReader = PacketReaders.readerOf(event);
          arguments[readerIndex] = packetReader;
        }
        calledMethod.invoke(subscriber, arguments);
      } catch (ReflectiveOperationException exception) {
        throw new RuntimeException(
          "Failed to invoke packet subscription " + calledMethod + " in " + subscriber.getClass().getCanonicalName(),
          exception
        );
      } catch (RuntimeException exception) {
        if (readerIndex != -1 && packetReader == null) {
          IntaveLogger.logger().warn(
            "Skipped incompatible PacketEvents reader for " + event.getPacketType().getName() + " in "
              + subscriber.getClass().getCanonicalName() + "." + calledMethod.getName()
          );
          return;
        }
        throw exception;
      } finally {
        if (packetReader != null) {
          packetReader.releaseSafe();
        }
        Arrays.fill(arguments, null);
        pool.offerFirst(arguments);
      }
    };
  }

  private static int findParameterPosition(Class<?>[] parameterTypes, Class<?> parameterType) {
    for (int i = 0; i < parameterTypes.length; i++) {
      if (parameterTypes[i] == parameterType || parameterType.isAssignableFrom(parameterTypes[i])) {
        return i;
      }
    }
    return -1;
  }

  private <T> T instanceOf(Class<T> clazz) {
    try {
      return clazz.newInstance();
    } catch (InstantiationException | IllegalAccessException exception) {
      throw new Error(exception);
    }
  }

  private String className(Class<?> clazz) {
    return clazz.getCanonicalName();
  }

  private String canonicalRepresentation(String input) {
    return input.replace('.', '/');
  }

  private final class DirectPacketEventsListener extends PacketListenerAbstract {
    private final FilteringPacketAdapter target;

    private DirectPacketEventsListener(FilteringPacketAdapter target) {
      super(target.priority().packetEventsPriority());
      this.target = target;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
      target.invoke(event);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
      target.invoke(event);
    }
  }
}

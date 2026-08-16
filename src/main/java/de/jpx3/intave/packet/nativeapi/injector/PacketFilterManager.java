package de.jpx3.intave.packet.nativeapi.injector;

import de.jpx3.intave.packet.nativeapi.PacketRuntimeManager;

/** PacketEvents-backed compatibility manager for legacy Intave call sites. */
public final class PacketFilterManager extends PacketRuntimeManager {
  @SuppressWarnings("unused")
  private final Object inboundListeners = new Object();
}

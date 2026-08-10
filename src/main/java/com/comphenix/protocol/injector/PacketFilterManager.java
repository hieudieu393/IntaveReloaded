package com.comphenix.protocol.injector;

import com.comphenix.protocol.ProtocolManager;

/** PacketEvents-backed compatibility manager for legacy Intave call sites. */
public final class PacketFilterManager extends ProtocolManager {
  @SuppressWarnings("unused")
  private final Object inboundListeners = new Object();
}

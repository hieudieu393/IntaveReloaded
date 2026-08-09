package com.comphenix.protocol.events;
public enum ConnectionSide {
  CLIENT_SIDE(true, false), SERVER_SIDE(false, true), BOTH(true, true);
  private final boolean client, server;
  ConnectionSide(boolean client, boolean server) { this.client=client; this.server=server; }
  public boolean isForClient() { return client; }
  public boolean isForServer() { return server; }
}

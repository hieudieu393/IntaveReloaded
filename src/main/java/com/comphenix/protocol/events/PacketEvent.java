package com.comphenix.protocol.events;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.comphenix.protocol.PacketType;
import org.bukkit.entity.Player;

public final class PacketEvent implements org.bukkit.event.Cancellable {
  private final ProtocolPacketEvent delegate;
  private PacketContainer packet;

  public PacketEvent(ProtocolPacketEvent delegate) {
    this.delegate = delegate;
    this.packet = PacketContainer.fromEvent(delegate);
  }

  public static PacketEvent from(PacketReceiveEvent event) { return new PacketEvent(event); }
  public static PacketEvent from(PacketSendEvent event) { return new PacketEvent(event); }
  public static PacketEvent fromServer(Object ignored, PacketContainer packet, Player player) { return new PacketEvent(null, packet, player); }
  public static PacketEvent fromClient(Object ignored, PacketContainer packet, Player player) { return new PacketEvent(null, packet, player); }

  private Player syntheticPlayer;
  private PacketEvent(ProtocolPacketEvent delegate, PacketContainer packet, Player player) {
    this.delegate=delegate; this.packet=packet; this.syntheticPlayer=player;
  }

  public Player getPlayer() { return delegate == null ? syntheticPlayer : delegate.getPlayer(); }
  public PacketContainer getPacket() { return packet; }
  public void setPacket(PacketContainer packet) {
    this.packet = packet;
    if (delegate != null && packet != null) delegate.setLastUsedWrapper(packet.wrapper());
  }
  public PacketType getPacketType() { return packet == null ? null : packet.getType(); }
  @Override public boolean isCancelled() { return delegate != null && delegate.isCancelled(); }
  @Override public void setCancelled(boolean cancelled) { if (delegate != null) delegate.setCancelled(cancelled); }
  public boolean isPlayerTemporary() { return getPlayer() == null; }
  public boolean isReadOnly() { return false; }
  public void setReadOnly(boolean ignored) {}
  public ProtocolPacketEvent delegate() { return delegate; }
}

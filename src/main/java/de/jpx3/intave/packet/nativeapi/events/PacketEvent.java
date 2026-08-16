package de.jpx3.intave.packet.nativeapi.events;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.packet.nativeapi.PacketType;
import org.bukkit.entity.Player;

public final class PacketEvent implements org.bukkit.event.Cancellable {
  private final ProtocolPacketEvent delegate;
  private NativePacket packet;

  public PacketEvent(ProtocolPacketEvent delegate) {
    this.delegate = delegate;
    this.packet = NativePacket.fromEvent(delegate);
  }

  public static PacketEvent from(PacketReceiveEvent event) { return new PacketEvent(event); }
  public static PacketEvent from(PacketSendEvent event) { return new PacketEvent(event); }
  public static PacketEvent fromServer(Object ignored, NativePacket packet, Player player) { return new PacketEvent(null, packet, player); }
  public static PacketEvent fromClient(Object ignored, NativePacket packet, Player player) { return new PacketEvent(null, packet, player); }

  private Player syntheticPlayer;
  private PacketEvent(ProtocolPacketEvent delegate, NativePacket packet, Player player) {
    this.delegate=delegate; this.packet=packet; this.syntheticPlayer=player;
  }

  public Player getPlayer() { return delegate == null ? syntheticPlayer : delegate.getPlayer(); }
  public NativePacket getPacket() { return packet; }
  public void setPacket(NativePacket packet) {
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

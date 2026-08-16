/* Copyright 2026 Intave */
package de.jpx3.intave.packet.reader;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.world.border.WorldBorder;
import org.jetbrains.annotations.NotNull;

import static de.jpx3.intave.packet.reader.WorldBorderReader.UpdateType.INITIALIZE;

public final class WorldBorderReader extends AbstractPacketReader {
  private UpdateType updateType;
  private double centerX;
  private double centerZ;
  private double size;
  private double oldSize;
  private double newSize;
  private long lerpTime;
  private int absoluteMaxSize;

  @Override
  protected void read() {
    PacketTypeCommon type = packetType();
    PacketWrapper<?> packet = rawPacket();
    if (type == PacketType.Play.Server.WORLD_BORDER_CENTER) {
      updateType = UpdateType.SET_CENTER;
      centerX = packet.readDouble();
      centerZ = packet.readDouble();
    } else if (type == PacketType.Play.Server.WORLD_BORDER_SIZE) {
      updateType = UpdateType.SET_SIZE;
      size = packet.readDouble();
    } else if (type == PacketType.Play.Server.WORLD_BORDER_LERP_SIZE) {
      updateType = UpdateType.LERP_SIZE;
      oldSize = packet.readDouble();
      newSize = packet.readDouble();
      lerpTime = packet.readVarLong();
    } else if (type == PacketType.Play.Server.INITIALIZE_WORLD_BORDER) {
      updateType = INITIALIZE;
      centerX = packet.readDouble();
      centerZ = packet.readDouble();
      oldSize = packet.readDouble();
      newSize = packet.readDouble();
      lerpTime = packet.readVarLong();
      absoluteMaxSize = packet.readVarInt();
      packet.readVarInt(); // warning time
      packet.readVarInt(); // warning distance
      size = newSize;
    } else if (type == PacketType.Play.Server.WORLD_BORDER_WARNING_DELAY) {
      updateType = UpdateType.SET_WARNING_TIME;
    } else if (type == PacketType.Play.Server.WORLD_BORDER_WARNING_REACH) {
      updateType = UpdateType.SET_WARNING_BLOCKS;
    } else {
      throw new IllegalStateException("Unknown native world border packet type: " + type.getName());
    }
  }

  public @NotNull WorldBorder updated(@NotNull WorldBorder worldBorder) {
    if (updateType.updatesCenter()) worldBorder = worldBorder.withCenterAt(new Position(centerX, 0, centerZ));
    if (updateType.updatesRawSize()) worldBorder = worldBorder.withSize(size);
    if (updateType.updatesLerpSize()) worldBorder = worldBorder.withLerpingSize(oldSize, newSize, lerpTime);
    if (updateType.updatesAbsoluteMaxSize()) worldBorder = worldBorder.withAbsoluteMaxSize(absoluteMaxSize);
    return worldBorder;
  }

  public enum UpdateType {
    SET_SIZE, LERP_SIZE, SET_CENTER, INITIALIZE, SET_WARNING_TIME, SET_WARNING_BLOCKS;
    public boolean updatesCenter() { return this == SET_CENTER || this == INITIALIZE; }
    public boolean updatesRawSize() { return this == SET_SIZE || this == INITIALIZE; }
    public boolean updatesLerpSize() { return this == LERP_SIZE || this == INITIALIZE; }
    public boolean updatesAbsoluteMaxSize() { return this == INITIALIZE; }
  }
}

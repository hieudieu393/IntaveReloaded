package de.jpx3.intave.packet.reader;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientVehicleMove;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.Rotation;

public final class PlayerMoveReader extends AbstractPacketReader {
  private WrapperPlayClientPlayerFlying flying;
  private WrapperPlayClientVehicleMove vehicle;

  @Override
  protected void read() {
    if (isVehicleMove()) {
      vehicle = new WrapperPlayClientVehicleMove(receiveEvent());
      flying = null;
    } else {
      flying = new WrapperPlayClientPlayerFlying(receiveEvent());
      vehicle = null;
    }
  }

  public boolean isVehicleMove() {
    return packetType() == PacketType.Play.Client.VEHICLE_MOVE;
  }

  public double positionX() {
    return isVehicleMove() ? vehicle.getPosition().getX() : flying.getLocation().getX();
  }

  public double positionY() {
    return isVehicleMove() ? vehicle.getPosition().getY() : flying.getLocation().getY();
  }

  public double positionZ() {
    return isVehicleMove() ? vehicle.getPosition().getZ() : flying.getLocation().getZ();
  }

  public @Nullable Position position() {
    return hasMovement() ? new Position(positionX(), positionY(), positionZ()) : null;
  }

  public float yaw() {
    return isVehicleMove() ? vehicle.getYaw() : flying.getLocation().getYaw();
  }

  public float pitch() {
    return isVehicleMove() ? vehicle.getPitch() : flying.getLocation().getPitch();
  }

  public @Nullable Rotation rotation() {
    return hasRotation() ? new Rotation(yaw(), pitch()) : null;
  }

  public boolean onGround() {
    return isVehicleMove() ? vehicle.isOnGround() : flying.isOnGround();
  }

  public void setOnGround(boolean onGround) {
    if (isVehicleMove()) vehicle.setOnGround(onGround); else flying.setOnGround(onGround);
    event().markForReEncode(true);
  }

  public void setPositionX(double x) {
    setPosition(new Position(x, positionY(), positionZ()));
  }

  public void setPositionY(double y) {
    setPosition(new Position(positionX(), y, positionZ()));
  }

  public void setPositionZ(double z) {
    setPosition(new Position(positionX(), positionY(), z));
  }

  public void setPosition(Position position) {
    if (isVehicleMove()) {
      vehicle.setPosition(new Vector3d(position.getX(), position.getY(), position.getZ()));
    } else {
      flying.setLocation(new com.github.retrooper.packetevents.protocol.world.Location(
        position.getX(), position.getY(), position.getZ(), yaw(), pitch()
      ));
    }
    event().markForReEncode(true);
  }

  public void setYaw(float yaw) {
    if (isVehicleMove()) {
      vehicle.setYaw(yaw);
    } else {
      flying.setLocation(new com.github.retrooper.packetevents.protocol.world.Location(
        positionX(), positionY(), positionZ(), yaw, pitch()
      ));
    }
    event().markForReEncode(true);
  }

  public void setPitch(float pitch) {
    if (isVehicleMove()) {
      vehicle.setPitch(pitch);
    } else {
      flying.setLocation(new com.github.retrooper.packetevents.protocol.world.Location(
        positionX(), positionY(), positionZ(), yaw(), pitch
      ));
    }
    event().markForReEncode(true);
  }

  public boolean hasMovement() {
    return isVehicleMove() || flying.hasPositionChanged();
  }

  public boolean hasRotation() {
    return isVehicleMove() || flying.hasRotationChanged();
  }

  public boolean anyNaNOrInfiniteValue() {
    if (hasMovement() && (!Double.isFinite(positionX()) || !Double.isFinite(positionY()) || !Double.isFinite(positionZ()))) {
      return true;
    }
    return hasRotation() && (!Float.isFinite(yaw()) || !Float.isFinite(pitch()));
  }

  @Override
  public void release() {
    flying = null;
    vehicle = null;
    super.release();
  }
}

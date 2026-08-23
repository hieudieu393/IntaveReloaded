package de.jpx3.intave.packet.reader;

import com.github.retrooper.packetevents.protocol.entity.EntityPositionData;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerPositionAndLook;
import de.jpx3.intave.packet.Relative;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.PositionMoveRotation;
import de.jpx3.intave.share.Rotation;

import java.util.Set;

/** Native PacketEvents view of the clientbound position synchronization packet. */
public final class PlayerTeleportReader extends AbstractPacketReader {
  private WrapperPlayServerPlayerPositionAndLook wrapper;
  private PositionMoveRotation values;
  private boolean modified;

  @Override
  protected void read() {
    wrapper = new WrapperPlayServerPlayerPositionAndLook(sendEvent());
    EntityPositionData nativeValues = wrapper.getValues();
    Vector3d position = nativeValues.getPosition();
    Vector3d delta = nativeValues.getDeltaMovement();
    values = new PositionMoveRotation(
      Position.mutableOf(position.getX(), position.getY(), position.getZ()),
      new Motion(delta.getX(), delta.getY(), delta.getZ()),
      new Rotation(nativeValues.getYaw(), nativeValues.getPitch())
    );
    modified = false;
  }

  public double positionX() { return values.position().getX(); }
  public void setPositionX(double x) { values.position().setX(x); modified = true; }
  public double positionY() { return values.position().getY(); }
  public void setPositionY(double y) { values.position().setY(y); modified = true; }
  public double positionZ() { return values.position().getZ(); }
  public void setPositionZ(double z) { values.position().setZ(z); modified = true; }
  public Position position() { modified = true; return values.position(); }
  public float yaw() { return values.rotation().yaw(); }
  public void setYaw(float yaw) { values.rotation().setYaw(yaw); modified = true; }
  public float pitch() { return values.rotation().pitch(); }
  public void setPitch(float pitch) { values.rotation().setPitch(pitch); modified = true; }
  public Rotation rotation() { modified = true; return values.rotation(); }
  public double motionX() { return values.motion().motionX(); }
  public void setMotionX(double x) { values.motion().setMotionX(x); modified = true; }
  public double motionY() { return values.motion().motionY(); }
  public void setMotionY(double y) { values.motion().setMotionY(y); modified = true; }
  public double motionZ() { return values.motion().motionZ(); }
  public void setMotionZ(double z) { values.motion().setMotionZ(z); modified = true; }
  public Motion motion() { modified = true; return values.motion(); }
  public PositionMoveRotation positionMoveRotation() { modified = true; return values; }

  @Override
  public void flush() {
    if (modified) {
      Position position = values.position();
      Motion motion = values.motion();
      Rotation rotation = values.rotation();
      wrapper.setValues(new EntityPositionData(
        new Vector3d(position.getX(), position.getY(), position.getZ()),
        new Vector3d(motion.motionX(), motion.motionY(), motion.motionZ()),
        rotation.yaw(), rotation.pitch()
      ));
    }
    modified = false;
    super.flush();
  }

  @Override
  public void release() {
    flush();
    wrapper = null;
    values = null;
    super.release();
  }

  public Set<Relative> flags() {
    return Relative.fromPacketEvents(wrapper.getRelativeFlags());
  }

  public void setFlags(Set<Relative> flags) {
    wrapper.setRelativeFlags(Relative.toPacketEvents(flags));
  }
}

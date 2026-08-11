package de.jpx3.intave.packet.reader;

import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity;
import de.jpx3.intave.annotate.Unmodifiable;
import de.jpx3.intave.share.Motion;

public class EntityVelocityReader extends EntityReader {
  private WrapperPlayServerEntityVelocity wrapper;

  @Override
  protected void read() {
    wrapper = new WrapperPlayServerEntityVelocity(sendEvent());
    entityId(wrapper.getEntityId());
  }

  public double motionX() { return wrapper.getVelocity().getX(); }
  public double motionY() { return wrapper.getVelocity().getY(); }
  public double motionZ() { return wrapper.getVelocity().getZ(); }

  public @Unmodifiable Motion motion() {
    Vector3d velocity = wrapper.getVelocity();
    return new Motion(velocity.getX(), velocity.getY(), velocity.getZ());
  }

  public void setMotionX(double motionX) {
    Vector3d v = wrapper.getVelocity();
    wrapper.setVelocity(new Vector3d(motionX, v.getY(), v.getZ()));
    event().markForReEncode(true);
  }

  public void setMotionY(double motionY) {
    Vector3d v = wrapper.getVelocity();
    wrapper.setVelocity(new Vector3d(v.getX(), motionY, v.getZ()));
    event().markForReEncode(true);
  }

  public void setMotionZ(double motionZ) {
    Vector3d v = wrapper.getVelocity();
    wrapper.setVelocity(new Vector3d(v.getX(), v.getY(), motionZ));
    event().markForReEncode(true);
  }

  public void setMotion(Motion motion) {
    wrapper.setVelocity(new Vector3d(motion.motionX(), motion.motionY(), motion.motionZ()));
    event().markForReEncode(true);
  }

  @Override
  public void release() {
    wrapper = null;
    super.release();
  }
}

/* Copyright 2026 Intave */
package de.jpx3.intave.packet.reader;

import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerExplosion;
import de.jpx3.intave.share.Motion;

public final class ExplosionReader extends AbstractPacketReader {
  private WrapperPlayServerExplosion wrapper;

  @Override
  protected void read() {
    wrapper = new WrapperPlayServerExplosion(sendEvent());
  }

  public Motion motion() {
    Vector3d knockback = wrapper.getKnockback();
    return knockback == null ? null : new Motion(knockback.getX(), knockback.getY(), knockback.getZ());
  }

  public void setMotion(Motion motion) {
    wrapper.setKnockback(motion == null ? null : new Vector3d(motion.motionX(), motion.motionY(), motion.motionZ()));
    event().markForReEncode(true);
  }

  @Override
  public void release() {
    wrapper = null;
    super.release();
  }
}

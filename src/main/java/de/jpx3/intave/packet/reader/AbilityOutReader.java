package de.jpx3.intave.packet.reader;

import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerAbilities;

public class AbilityOutReader extends AbstractPacketReader {
  private WrapperPlayServerPlayerAbilities wrapper;

  @Override
  protected void read() {
    wrapper = new WrapperPlayServerPlayerAbilities(sendEvent());
  }

  public float flyingSpeed() {
    return wrapper.getFlySpeed();
  }

  public float walkingSpeed() {
    return wrapper.getFOVModifier();
  }

  public boolean flyingAllowed() {
    return wrapper.isFlightAllowed();
  }

  @Override
  public void release() {
    wrapper = null;
    super.release();
  }
}

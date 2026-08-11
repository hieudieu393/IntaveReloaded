package de.jpx3.intave.packet.reader;

import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerAbilities;

public class AbilityInReader extends AbstractPacketReader {
  private WrapperPlayClientPlayerAbilities wrapper;

  @Override
  protected void read() {
    wrapper = new WrapperPlayClientPlayerAbilities(receiveEvent());
  }

  public boolean requestedFlying() {
    return wrapper.isFlying();
  }

  @Override
  public void release() {
    wrapper = null;
    super.release();
  }
}

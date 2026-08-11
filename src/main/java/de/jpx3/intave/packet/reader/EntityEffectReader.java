package de.jpx3.intave.packet.reader;

import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEffect;

public final class EntityEffectReader extends EntityReader {
  private WrapperPlayServerEntityEffect wrapper;

  @Override
  protected void read() {
    wrapper = new WrapperPlayServerEntityEffect(sendEvent());
    entityId(wrapper.getEntityId());
  }

  public int effectType() {
    return wrapper.getPotionType() == null ? 0
      : wrapper.getPotionType().getId(sendEvent().getUser().getClientVersion());
  }

  public int effectAmplifier() {
    return wrapper.getEffectAmplifier();
  }

  public int effectDuration() {
    return wrapper.getEffectDurationTicks();
  }

  @Override
  public void release() {
    wrapper = null;
    super.release();
  }
}

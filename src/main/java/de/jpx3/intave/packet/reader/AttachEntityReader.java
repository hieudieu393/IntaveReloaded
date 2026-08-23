package de.jpx3.intave.packet.reader;

import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerAttachEntity;
import org.jetbrains.annotations.NotNull;

public final class AttachEntityReader extends AbstractPacketReader implements EntityIterable {
  private WrapperPlayServerAttachEntity wrapper;

  @Override
  protected void read() {
    wrapper = new WrapperPlayServerAttachEntity(sendEvent());
  }

  public int entityId() {
    return wrapper.getAttachedId();
  }

  public int vehicleId() {
    return wrapper.getHoldingId();
  }

  private int slot = 0;
  private final SubstitutionIterator<Integer> STATIC_ITERATOR = new SubstitutionIterator<Integer>() {
    @Override
    public void set(Integer integer) {
      if (slot == 1) {
        wrapper.setAttachedId(integer);
      } else if (slot == 2) {
        wrapper.setHoldingId(integer);
      }
    }

    @Override
    public boolean hasNext() {
      return slot < 2;
    }

    @Override
    public Integer next() {
      return slot++ == 0 ? entityId() : vehicleId();
    }
  };

  @Override
  public @NotNull SubstitutionIterator<Integer> iterator() {
    slot = 0;
    return STATIC_ITERATOR;
  }

  @Override
  public void release() {
    wrapper = null;
    super.release();
  }
}

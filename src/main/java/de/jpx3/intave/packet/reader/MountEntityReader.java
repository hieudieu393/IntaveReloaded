package de.jpx3.intave.packet.reader;

import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import org.jetbrains.annotations.NotNull;

public final class MountEntityReader extends EntityReader implements EntityIterable {
  private WrapperPlayServerSetPassengers wrapper;

  @Override
  protected void read() {
    wrapper = new WrapperPlayServerSetPassengers(sendEvent());
    entityId(wrapper.getEntityId());
  }

  public int entityId() {
    return wrapper.getEntityId();
  }

  public int[] mounts() {
    return wrapper.getPassengers();
  }

  @Override
  public @NotNull SubstitutionIterator<Integer> iterator() {
    return new SubstitutionIterator<Integer>() {
      private int slot = 0;

      @Override
      public boolean hasNext() {
        return slot < 1 + mounts().length;
      }

      @Override
      public Integer next() {
        if (slot == 0) {
          slot++;
          return entityId();
        } else {
          return mounts()[slot++ - 1];
        }
      }

      @Override
      public void set(Integer integer) {
        if (slot == 1) {
          wrapper.setEntityId(integer);
        } else {
          int[] passengers = mounts();
          passengers[slot - 2] = integer;
          wrapper.setPassengers(passengers);
        }
      }
    };
  }

  @Override
  public void release() {
    wrapper = null;
    super.release();
  }
}

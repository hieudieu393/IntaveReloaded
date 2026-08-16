package de.jpx3.intave.packet.reader;

import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow;
import org.jetbrains.annotations.NotNull;

import java.util.NoSuchElementException;
import java.util.Optional;

/** Native PacketEvents reader for OPEN_WINDOW and OPEN_HORSE_WINDOW. */
public final class WindowOpenReader extends AbstractPacketReader implements EntityIterable {
  private WrapperPlayServerOpenWindow wrapper;

  @Override
  protected void read() {
    wrapper = new WrapperPlayServerOpenWindow(sendEvent());
  }

  public int containerId() {
    return wrapper.getContainerId();
  }

  public int slots() {
    int legacySlots = wrapper.getLegacySlots();
    if (legacySlots > 0) return legacySlots;
    switch (wrapper.getType()) {
      case 0: return 9;
      case 1: return 18;
      case 2: return 27;
      case 3: return 36;
      case 4: return 45;
      case 5: return 54;
      default: return 27;
    }
  }

  public Optional<Integer> optionalEntityId() {
    int entityId = wrapper.getHorseId();
    return entityId == 0 ? Optional.empty() : Optional.of(entityId);
  }

  @Override
  public @NotNull SubstitutionIterator<Integer> iterator() {
    return new SubstitutionIterator<Integer>() {
      private final boolean available = optionalEntityId().isPresent();
      private boolean returned;

      @Override
      public void set(Integer integer) {
        if (!returned) throw new IllegalStateException("next() must be called before set()");
        wrapper.setHorseId(integer);
      }

      @Override
      public boolean hasNext() {
        return available && !returned;
      }

      @Override
      public Integer next() {
        if (!hasNext()) throw new NoSuchElementException();
        returned = true;
        return wrapper.getHorseId();
      }
    };
  }

  @Override
  public void release() {
    wrapper = null;
    super.release();
  }
}

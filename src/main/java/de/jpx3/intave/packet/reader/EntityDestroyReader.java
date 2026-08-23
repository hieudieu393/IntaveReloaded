package de.jpx3.intave.packet.reader;

import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import org.jetbrains.annotations.NotNull;

import java.util.NoSuchElementException;
import java.util.function.Consumer;

public final class EntityDestroyReader extends AbstractPacketReader implements EntityIterable {
  private WrapperPlayServerDestroyEntities wrapper;
  private int[] entityIds;

  @Override
  protected void read() {
    wrapper = new WrapperPlayServerDestroyEntities(sendEvent());
    entityIds = wrapper.getEntityIds();
  }

  @Override
  public void forEach(Consumer<? super Integer> action) {
    for (int entityId : entityIds) action.accept(entityId);
  }

  @NotNull
  @Override
  public SubstitutionIterator<Integer> iterator() {
    return new SubstitutionIterator<Integer>() {
      private int index;

      @Override
      public void set(Integer integer) {
        if (index == 0) throw new IllegalStateException("next() must be called before set()");
        entityIds[index - 1] = integer;
        wrapper.setEntityIds(entityIds);
      }

      @Override
      public boolean hasNext() {
        return index < entityIds.length;
      }

      @Override
      public Integer next() {
        if (!hasNext()) throw new NoSuchElementException();
        return entityIds[index++];
      }
    };
  }

  @Override
  public void release() {
    wrapper = null;
    entityIds = null;
    super.release();
  }
}

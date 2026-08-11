/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 */
package de.jpx3.intave.packet.reader;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.entity.EntityLookup;
import de.jpx3.intave.user.User;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;

/** Native PacketEvents entity-id reader for packets whose first field is an entity id. */
public class EntityReader extends AbstractPacketReader implements EntityIterable {
  private int entityId;
  private boolean pendingIdAccess;

  @Override
  protected void read() {
    entityId = rawPacket().readVarInt();
    pendingIdAccess = true;
  }

  protected final void entityId(int entityId) {
    this.entityId = entityId;
  }

  public int entityId() {
    return entityId;
  }

  public @Nullable Entity entityBy(ProtocolPacketEvent event) {
    return entityBy(((org.bukkit.entity.Player) event.getPlayer()).getWorld());
  }

  public @Nullable Entity entityBy(World world) {
    return EntityLookup.findEntity(world, entityId());
  }

  public boolean targetEntityIdIsSameAs(User user) {
    return user.hasPlayer() && user.player().getEntityId() == entityId();
  }

  @NotNull
  @Override
  public SubstitutionIterator<Integer> iterator() {
    pendingIdAccess = true;
    return new SubstitutionIterator<Integer>() {
      @Override
      public boolean hasNext() {
        return pendingIdAccess;
      }

      @Override
      public Integer next() {
        if (!pendingIdAccess) {
          throw new NoSuchElementException();
        }
        pendingIdAccess = false;
        return entityId;
      }

      @Override
      public void set(Integer integer) {
        // EntityIdFilter is disabled. Native PacketEvents wrappers must own re-encoding for packet
        // types that need entity-id substitution; silently mutating a detached compatibility
        // container would be incorrect.
        throw new UnsupportedOperationException("Native entity-id substitution requires a typed PacketEvents wrapper");
      }
    };
  }

  @Override
  public void forEach(Consumer<? super Integer> action) {
    action.accept(entityId());
  }

  @Override
  public Spliterator<Integer> spliterator() {
    return Spliterators.spliterator(iterator(), 1, 0);
  }

  @Override
  public void release() {
    entityId = 0;
    pendingIdAccess = false;
    super.release();
  }
}

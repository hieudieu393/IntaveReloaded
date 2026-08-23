package de.jpx3.intave.packet.reader;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.Combat;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCombatEvent;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDeathCombatEvent;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEndCombatEvent;
import org.jetbrains.annotations.NotNull;

import java.util.NoSuchElementException;

/** Native view shared by the legacy and split combat-event packets. */
public final class CombatEventReader extends AbstractPacketReader implements EntityIterable {
  private WrapperPlayServerCombatEvent legacy;
  private WrapperPlayServerDeathCombatEvent death;
  private WrapperPlayServerEndCombatEvent end;
  private int slot;

  @Override
  protected void read() {
    if (packetType() == PacketType.Play.Server.DEATH_COMBAT_EVENT) {
      death = new WrapperPlayServerDeathCombatEvent(sendEvent());
    } else if (packetType() == PacketType.Play.Server.END_COMBAT_EVENT) {
      end = new WrapperPlayServerEndCombatEvent(sendEvent());
    } else if (packetType() == PacketType.Play.Server.COMBAT_EVENT) {
      legacy = new WrapperPlayServerCombatEvent(sendEvent());
    }
    slot = 0;
  }

  public int firstEntityId() {
    if (death != null) return death.getPlayerId();
    if (end != null) return end.getDuration();
    if (legacy == null || legacy.getCombat() == Combat.ENTER_COMBAT) return 0;
    return legacy.getCombat() == Combat.ENTITY_DEAD ? legacy.getPlayerId() : legacy.getDuration();
  }

  public int secondEntityId() {
    if (death != null) return death.getEntityId().orElse(0);
    if (end != null) return end.getEntityId().orElse(0);
    return legacy == null ? 0 : legacy.getEntityId();
  }

  private void setAt(int index, int value) {
    if (index == 0) {
      if (death != null) death.setPlayerId(value);
      else if (end != null) end.setDuration(value);
      else if (legacy != null && legacy.getCombat() == Combat.ENTITY_DEAD) legacy.setPlayerId(value);
      else if (legacy != null && legacy.getCombat() == Combat.END_COMBAT) legacy.setDuration(value);
    } else {
      if (death != null) death.setEntityId(value);
      else if (end != null) end.setEntityId(value);
      else if (legacy != null) legacy.setEntityId(value);
    }
  }

  @Override
  public @NotNull SubstitutionIterator<Integer> iterator() {
    slot = 0;
    return new SubstitutionIterator<Integer>() {
      private int last = -1;

      @Override
      public void set(Integer integer) {
        if (last < 0) throw new IllegalStateException("next() has not been called");
        setAt(last, integer);
      }

      @Override
      public boolean hasNext() {
        return slot < 2;
      }

      @Override
      public Integer next() {
        if (!hasNext()) throw new NoSuchElementException();
        last = slot++;
        return last == 0 ? firstEntityId() : secondEntityId();
      }
    };
  }

  @Override
  public void release() {
    legacy = null;
    death = null;
    end = null;
    slot = 0;
    super.release();
  }
}

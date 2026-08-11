package de.jpx3.intave.packet.reader;

import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;
import de.jpx3.intave.packet.converter.PlayerAction;

public final class PlayerActionReader extends AbstractPacketReader {
  private WrapperPlayClientEntityAction wrapper;

  @Override
  protected void read() {
    wrapper = new WrapperPlayClientEntityAction(receiveEvent());
  }

  public PlayerAction playerAction() {
    if (wrapper == null) {
      return null;
    }
    switch (wrapper.getAction()) {
      case START_SNEAKING:
        return PlayerAction.START_SNEAKING;
      case STOP_SNEAKING:
        return PlayerAction.STOP_SNEAKING;
      case LEAVE_BED:
        return PlayerAction.STOP_SLEEPING;
      case START_SPRINTING:
        return PlayerAction.START_SPRINTING;
      case STOP_SPRINTING:
        return PlayerAction.STOP_SPRINTING;
      case START_JUMPING_WITH_HORSE:
        return PlayerAction.START_RIDING_JUMP;
      case STOP_JUMPING_WITH_HORSE:
        return PlayerAction.STOP_RIDING_JUMP;
      case OPEN_HORSE_INVENTORY:
        return PlayerAction.OPEN_INVENTORY;
      case START_FLYING_WITH_ELYTRA:
        return PlayerAction.START_FALL_FLYING;
      default:
        return null;
    }
  }

  public int entityId() {
    return wrapper == null ? -1 : wrapper.getEntityId();
  }

  public int jumpBoost() {
    return wrapper == null ? 0 : wrapper.getJumpBoost();
  }

  @Override
  public void release() {
    wrapper = null;
    super.release();
  }
}

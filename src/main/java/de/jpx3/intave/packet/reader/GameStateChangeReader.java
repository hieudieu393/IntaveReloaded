package de.jpx3.intave.packet.reader;

import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChangeGameState;

public final class GameStateChangeReader extends AbstractPacketReader {
  private WrapperPlayServerChangeGameState wrapper;

  @Override
  protected void read() {
    wrapper = new WrapperPlayServerChangeGameState(sendEvent());
  }

  public GameState type() {
    int index = wrapper.getReason().ordinal();
    return index >= 0 && index < GameState.values().length ? GameState.values()[index] : GameState.INVALID_BED;
  }

  public float value() {
    return wrapper.getValue();
  }

  public int valueAsInt() {
    return (int) (value() + 0.5F);
  }

  @Override
  public void release() {
    wrapper = null;
    super.release();
  }

  public enum GameState {
    INVALID_BED(0), END_RAIN(1), BEGIN_RAIN(2), CHANGE_GAME_MODE(3), ENTER_CREDITS(4),
    DEMO_MESSAGE(5), ARROW_HITTING_PLAYER(6), RAIN_LEVEL_CHANGE(7), THUNDER_LEVEL_CHANGE(8),
    PLAY_MOB_APPEARANCE(9), PLAY_MOB2_APPEARANCE(10), ENABLE_RESPAWN_SCREEN(11);

    private final int id;
    GameState(int id) { this.id = id; }
    public int id() { return id; }
  }
}

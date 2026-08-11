package de.jpx3.intave.packet.reader;

import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class PlayerInfoReader extends AbstractPacketReader {
  private WrapperPlayServerPlayerInfoUpdate wrapper;

  @Override
  protected void read() {
    wrapper = new WrapperPlayServerPlayerInfoUpdate(sendEvent());
  }

  public Set<WrapperPlayServerPlayerInfoUpdate.Action> playerInfoActions() {
    return wrapper == null ? Collections.emptySet() : wrapper.getActions();
  }

  public List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> playerInfoData() {
    return wrapper == null ? Collections.emptyList() : wrapper.getEntries();
  }

  public void writePlayerInfoData(List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> playerInfos) {
    wrapper.setEntries(playerInfos);
    event().markForReEncode(true);
  }

  @Override
  public void release() {
    wrapper = null;
    super.release();
  }
}

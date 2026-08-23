package de.jpx3.intave.packet.reader;

import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class PlayerInfoRemoveReader extends AbstractPacketReader {
  private List<UUID> profileIds = Collections.emptyList();

  @Override
  protected void read() {
    profileIds = new WrapperPlayServerPlayerInfoRemove(sendEvent()).getProfileIds();
  }

  public List<UUID> playersToRemove() {
    return profileIds;
  }

  @Override
  public void release() {
    profileIds = Collections.emptyList();
    super.release();
  }
}

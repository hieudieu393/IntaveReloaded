/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.packet.reader;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientWindowConfirmation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowConfirmation;
import de.jpx3.intave.annotate.Nullable;

public final class TransactionReader extends AbstractPacketReader {
  private Integer containerId;
  private Short actionNumber;
  private Boolean accepted;

  @Override
  protected void read() {
    if (event() instanceof PacketReceiveEvent) {
      WrapperPlayClientWindowConfirmation wrapper = new WrapperPlayClientWindowConfirmation(receiveEvent());
      containerId = wrapper.getWindowId();
      actionNumber = wrapper.getActionId();
      accepted = wrapper.isAccepted();
    } else {
      WrapperPlayServerWindowConfirmation wrapper = new WrapperPlayServerWindowConfirmation(sendEvent());
      containerId = wrapper.getWindowId();
      actionNumber = wrapper.getActionId();
      accepted = wrapper.isAccepted();
    }
  }

  public @Nullable Integer containerId() {
    return containerId;
  }

  public @Nullable Short actionNumber() {
    return actionNumber;
  }

  public @Nullable Boolean accepted() {
    return accepted;
  }

  public boolean isRejected() {
    return Boolean.FALSE.equals(accepted());
  }

  @Override
  public void release() {
    containerId = null;
    actionNumber = null;
    accepted = null;
    super.release();
  }
}

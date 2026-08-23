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
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCloseWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindowButton;

/** Reads the window/container ID shared by simple inventory actions. */
public final class WindowIdReader extends AbstractPacketReader {
  private int containerId;

  @Override
  protected void read() {
    if (!(event() instanceof PacketReceiveEvent)) {
      throw new IllegalStateException("WindowIdReader only supports inbound window packets");
    }
    containerId = packetType() == com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.CLOSE_WINDOW
      ? new WrapperPlayClientCloseWindow(receiveEvent()).getWindowId()
      : new WrapperPlayClientClickWindowButton(receiveEvent()).getWindowId();
  }

  public int containerId() {
    return containerId;
  }
}

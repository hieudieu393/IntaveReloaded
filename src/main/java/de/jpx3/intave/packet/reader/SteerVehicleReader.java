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

import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientSteerVehicle;

public final class SteerVehicleReader extends AbstractPacketReader {
  private boolean jumping;

  @Override
  protected void read() {
    jumping = new WrapperPlayClientSteerVehicle(receiveEvent()).isJump();
  }

  public boolean isJumping() {
    return jumping;
  }

  @Override
  public void release() {
    jumping = false;
    super.release();
  }
}

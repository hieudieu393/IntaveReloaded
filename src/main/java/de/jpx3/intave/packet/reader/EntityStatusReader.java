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

import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityStatus;
import de.jpx3.intave.annotate.Nullable;

public final class EntityStatusReader extends EntityReader {
  private static final byte ITEM_USE_FINISHED = 9;
  private Byte status;

  @Override
  protected void read() {
    WrapperPlayServerEntityStatus wrapper = new WrapperPlayServerEntityStatus(sendEvent());
    entityId(wrapper.getEntityId());
    status = (byte) wrapper.getStatus();
  }

  public @Nullable Byte status() {
    return status;
  }

  public boolean indicatesItemUseFinished() {
    Byte status = status();
    return status != null && status == ITEM_USE_FINISHED;
  }

  @Override
  public void release() {
    status = null;
    super.release();
  }
}

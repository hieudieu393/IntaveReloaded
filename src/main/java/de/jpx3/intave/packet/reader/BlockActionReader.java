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

import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockAction;
import de.jpx3.intave.share.BlockPosition;
import org.bukkit.Material;

public final class BlockActionReader extends AbstractPacketReader {
  private WrapperPlayServerBlockAction wrapper;

  @Override
  protected void read() {
    wrapper = new WrapperPlayServerBlockAction(sendEvent());
  }

  public BlockPosition blockPosition() {
    return BlockPosition.fromPacketEvents(wrapper.getBlockPosition());
  }

  public Material blockType() {
    String name = wrapper.getBlockType().getType().getName();
    int separator = name.indexOf(':');
    return Material.matchMaterial((separator < 0 ? name : name.substring(separator + 1)).toUpperCase(java.util.Locale.ROOT));
  }

  public int action() {
    return wrapper.getActionId();
  }

  public int data() {
    return wrapper.getActionData();
  }

  @Override
  public void release() {
    wrapper = null;
    super.release();
  }
}

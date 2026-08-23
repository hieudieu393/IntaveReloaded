/* Copyright 2026 Intave */
package de.jpx3.intave.packet.reader;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity.InteractAction;

public final class EntityUseReader extends EntityReader {
  private WrapperPlayClientInteractEntity wrapper;
  private InteractAction action;

  @Override
  protected void read() {
    wrapper = null;
    if (packetType() == PacketType.Play.Client.ATTACK) {
      entityId(rawPacket().readVarInt());
      action = InteractAction.ATTACK;
      return;
    }
    wrapper = new WrapperPlayClientInteractEntity(receiveEvent());
    entityId(wrapper.getEntityId());
    action = wrapper.getAction();
  }

  public boolean isAttackPacket() {
    return action == InteractAction.ATTACK;
  }

  public boolean isSecondary() {
    return action == InteractAction.INTERACT_AT;
  }

  public InteractAction useAction() {
    return action;
  }

  @Override
  public void release() {
    wrapper = null;
    action = null;
    super.release();
  }
}

package de.jpx3.intave.packet.reader;

import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;

public final class PayloadInReader extends AbstractPacketReader {
  private WrapperPlayClientPluginMessage wrapper;

  @Override
  protected void read() {
    wrapper = new WrapperPlayClientPluginMessage(receiveEvent());
  }

  public String tag() {
    String tag = wrapper.getChannelName();
    return tag != null && tag.startsWith("minecraft:") ? tag.substring("minecraft:".length()) : tag;
  }

  public ByteBuf readBytes() {
    return Unpooled.wrappedBuffer(wrapper.getData()).asReadOnly();
  }

  public String readStringNormal() {
    return new String(wrapper.getData(), StandardCharsets.UTF_8);
  }

  public String readStringWithExtraByte() {
    byte[] data = wrapper.getData();
    return data.length <= 1 ? "" : new String(data, 1, data.length - 1, StandardCharsets.UTF_8);
  }

  @Override
  public void release() {
    wrapper = null;
    super.release();
  }
}

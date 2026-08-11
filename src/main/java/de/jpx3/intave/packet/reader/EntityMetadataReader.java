/* Copyright 2026 Intave */
package de.jpx3.intave.packet.reader;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import de.jpx3.intave.share.BlockPosition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class EntityMetadataReader extends EntityReader {
  private WrapperPlayServerEntityMetadata wrapper;
  private List<EntityData<?>> metadata = Collections.emptyList();

  @Override
  protected void read() {
    wrapper = new WrapperPlayServerEntityMetadata(sendEvent());
    entityId(wrapper.getEntityId());
    metadata = wrapper.getEntityMetadata();
  }

  public Optional<BlockPosition> bedPosition() {
    Object raw = fetchRaw(14); // supported runtime/client floor is 1.17+
    if (!(raw instanceof Optional<?>)) {
      return Optional.empty();
    }
    Object value = ((Optional<?>) raw).orElse(null);
    if (value instanceof Vector3i) {
      return Optional.of(BlockPosition.fromPacketEvents((Vector3i) value));
    }
    return Optional.empty();
  }

  public Object fetchRaw(int requiredIndex) {
    for (EntityData<?> data : metadata) {
      if (data.getIndex() == requiredIndex) {
        return data.getValue();
      }
    }
    return null;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  public void setRaw(int requiredIndex, Object newValue) {
    for (int i = 0; i < metadata.size(); i++) {
      EntityData<?> data = metadata.get(i);
      if (data.getIndex() == requiredIndex) {
        metadata.set(i, new EntityData(data.getIndex(), data.getType(), newValue));
        wrapper.setEntityMetadata(metadata);
        event().markForReEncode(true);
        return;
      }
    }
  }

  public Map<Integer, Object> entryMap() {
    Map<Integer, Object> result = new LinkedHashMap<>();
    for (EntityData<?> data : metadata) {
      result.put(data.getIndex(), data.getValue());
    }
    return result;
  }

  public List<Object> values() {
    List<Object> result = new ArrayList<>(metadata.size());
    for (EntityData<?> data : metadata) {
      result.add(data.getValue());
    }
    return result;
  }

  public List<EntityData<?>> metadataObjects() {
    return metadata;
  }

  public void setMetadataObjects(List<EntityData<?>> data) {
    metadata = data;
    wrapper.setEntityMetadata(data);
    event().markForReEncode(true);
  }

  @Override
  public void release() {
    wrapper = null;
    metadata = Collections.emptyList();
    super.release();
  }
}

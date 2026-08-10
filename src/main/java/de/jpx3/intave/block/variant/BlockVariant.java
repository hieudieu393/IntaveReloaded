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

package de.jpx3.intave.block.variant;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public interface BlockVariant {
  StreamCodec<ByteBuf, ByteBuf, BlockVariant> STREAM_CODEC =
    StreamCodec.dispatchBuilder(BlockVariant.class, ByteBufStreamCodecs.UNSIGNED_BYTE)
      .subtype(0, EmptyBlockVariant.class, () -> EmptyBlockVariant.STREAM_CODEC)
      .subtype(1, IndexedBlockVariant.class, () -> IndexedBlockVariant.STREAM_CODEC)
      .build();

  default BlockVariant copy() {
    Map<String, Comparable<?>> properties = new HashMap<>();
    for (String propertyName : propertyNames()) {
      properties.put(propertyName, propertyOf(propertyName));
    }
    return new IndexedBlockVariant(index(), properties);
  }

  Set<String> propertyNames();

  <T> T propertyOf(String name);

  <T extends Enum<T>> T enumProperty(Class<T> klass, String name);

  int index();

  void dumpStates();
}

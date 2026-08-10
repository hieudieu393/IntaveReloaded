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

import de.jpx3.intave.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

import java.util.Collections;
import java.util.Set;

final class EmptyBlockVariant implements BlockVariant {
  static final StreamCodec<ByteBuf, ByteBuf, EmptyBlockVariant> STREAM_CODEC =
    StreamCodec.of(new EmptyBlockVariant());

  @Override
  public BlockVariant copy() {
    return this;
  }

  @Override
  public Set<String> propertyNames() {
    return Collections.emptySet();
  }

  @Override
  public <T> T propertyOf(String name) {
    return null;
  }

  @Override
  public <T extends Enum<T>> T enumProperty(Class<T> klass, String name) {
    return null;
  }

  @Override
  public int index() {
    return 0;
  }

  @Override
  public void dumpStates() {
  }
}

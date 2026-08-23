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

package de.jpx3.intave.share;

import de.jpx3.intave.klass.locate.Locate;
import de.jpx3.intave.klass.locate.MethodSearchBySignature;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;

import static de.jpx3.intave.packet.nativeapi.utility.MinecraftReflection.getPacketDataSerializerClass;

public final class FriendlyByteBuf {
  public static ByteBuf from256Unpooled() {
    return wrapping(Unpooled.buffer(256, 2048));
  }

  public static ByteBuf wrapping(ByteBuf byteBuf) {
    return intaveFriendlyWrapping(byteBuf);
  }

  private final static Constructor<?> friendlyBufConstructor;

  static {
    try {
      friendlyBufConstructor = getPacketDataSerializerClass().getConstructor(ByteBuf.class);
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  private static ByteBuf intaveFriendlyWrapping(ByteBuf byteBuf) {
    try {
      return (ByteBuf) friendlyBufConstructor.newInstance(byteBuf);
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  public static String readUtf(ByteBuf friendly, int maxLength) {
    try {
      if (readUtfMethod == null) {
        return "something went wrong";
      }
      return (String) readUtfMethod.invoke(friendly, maxLength);
    } catch (Throwable e) {
      e.printStackTrace();
      return "something went wrong";
    }
  }

  public static void setup() {
  }

  private static final MethodHandle readUtfMethod;

  static {
    MethodHandle method;
    Class<?> rfbbclassoptional = Locate.classByKey("PacketDataSerializer");
    try {
      method = MethodHandles.lookup().unreflect(rfbbclassoptional.getDeclaredMethod("readUtf", int.class));
    } catch (NoSuchMethodException e) {
      method = MethodSearchBySignature.ofClass(getPacketDataSerializerClass())
        .withReturnType(String.class)
        .withParameters(new Class[]{int.class})
        .search().findFirst().get();
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
    readUtfMethod = method;
  }
}

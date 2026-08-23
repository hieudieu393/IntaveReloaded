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

package de.jpx3.intave.player.attribute;

import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.share.MinecraftKey;
import io.netty.buffer.ByteBuf;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static de.jpx3.intave.codec.ByteBufStreamCodecs.*;

public final class AttributeModifier {
  public static final StreamCodec<ByteBuf, ByteBuf, AttributeModifier> STREAM_CODEC = StreamCodec.of(
    (buffer, modifier) -> {
      MinecraftKey.STREAM_CODEC.nullable(BOOLEAN).encode(buffer, modifier.key);
      UUID.nullable(BOOLEAN).encode(buffer, modifier.uuid);
      STRING.encode(buffer, modifier.name == null ? "" : modifier.name);
      INTEGER.encode(buffer, modifier.operation.getId());
      DOUBLE.encode(buffer, modifier.amount);
    },
    buffer -> {
      MinecraftKey key = MinecraftKey.STREAM_CODEC.nullable(BOOLEAN).decode(buffer);
      java.util.UUID uuid = UUID.nullable(BOOLEAN).decode(buffer);
      String name = STRING.decode(buffer);
      return new AttributeModifier(key, uuid, name.isEmpty() ? null : name,
        Operation.fromId(INTEGER.decode(buffer)), DOUBLE.decode(buffer));
    }
  );

  private final MinecraftKey key;
  private final UUID uuid;
  private final String name;
  private final Operation operation;
  private final double amount;

  public AttributeModifier(MinecraftKey key, UUID uuid, String name, Operation operation, double amount) {
    this.key = key;
    this.uuid = uuid;
    this.name = name;
    this.operation = operation;
    this.amount = amount;
  }

  public MinecraftKey key() { return key; }
  public UUID id() { return uuid; }
  public String name() { return name; }
  public Operation operation() { return operation; }
  public double amount() { return amount; }

  public static Set<AttributeModifier> fromPacketEvents(
    java.util.List<WrapperPlayServerUpdateAttributes.PropertyModifier> modifiers
  ) {
    Set<AttributeModifier> set = new HashSet<>();
    for (WrapperPlayServerUpdateAttributes.PropertyModifier modifier : modifiers) {
      MinecraftKey key = modifier.getName() == null ? null : new MinecraftKey(modifier.getName().toString());
      set.add(new AttributeModifier(
        key,
        modifier.getUUID(),
        modifier.getName() == null ? null : modifier.getName().toString(),
        Operation.fromId(modifier.getOperation().ordinal()),
        modifier.getAmount()
      ));
    }
    return set;
  }

  @Override
  public String toString() {
    return "WrappedAttributeModifier{" + "key=" + key + ", uuid=" + uuid + ", name='" + name + '\''
      + ", operation=" + operation + ", amount=" + amount + '}';
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof AttributeModifier)) return false;
    AttributeModifier other = (AttributeModifier) obj;
    return Double.compare(amount, other.amount) == 0
      && Objects.equals(key == null ? null : key.fullKey(), other.key == null ? null : other.key.fullKey())
      && Objects.equals(uuid, other.uuid) && Objects.equals(name, other.name) && operation == other.operation;
  }

  @Override
  public int hashCode() { return Objects.hash(key == null ? null : key.fullKey(), uuid, name, operation, amount); }

  public static Builder newBuilder(UUID uuid) { return new Builder(uuid); }

  public static class Builder {
    private final UUID uuid;
    private MinecraftKey key;
    private String name;
    private Operation operation;
    private double amount;
    public Builder(UUID uuid) { this.uuid = uuid; }
    public Builder withKey(MinecraftKey key) { this.key = key; return this; }
    public Builder withName(String name) { this.name = name; return this; }
    public Builder withOperation(Operation operation) { this.operation = operation; return this; }
    public Builder withAmount(double amount) { this.amount = amount; return this; }
    public AttributeModifier build() {
      if (name == null || operation == null) throw new IllegalStateException("Key, name, and operation must be set");
      if (key == null) key = new MinecraftKey("intave", "custom_modifier");
      return new AttributeModifier(key, uuid, name, operation, amount);
    }
  }

  public enum Operation {
    ADD_NUMBER(0), MULTIPLY_PERCENTAGE(1), ADD_PERCENTAGE(2);
    private final int id;
    Operation(int id) { this.id = id; }
    public int getId() { return id; }
    public static Operation fromId(int id) {
      for (Operation op : values()) if (op.id == id) return op;
      throw new IllegalArgumentException("Invalid operation id: " + id);
    }
  }
}

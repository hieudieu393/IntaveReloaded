package com.comphenix.protocol.reflect;

import java.lang.reflect.*;
import java.util.*;

/** Reflection based field view used by the legacy Intave readers over PacketEvents wrappers. */
public class StructureModifier<T> {
  private final Class<?> targetClass;
  private Object target;
  private final Class<?> fieldType;
  private final EquivalentConverter<T> converter;

  public StructureModifier(Class<?> targetClass, Object ignored, boolean requireDefault) {
    this(targetClass, null, Object.class, null);
  }
  public StructureModifier(Object target) { this(target == null ? Object.class : target.getClass(), target, Object.class, null); }
  private StructureModifier(Class<?> targetClass, Object target, Class<?> fieldType, EquivalentConverter<T> converter) {
    this.targetClass=targetClass == null ? Object.class : targetClass; this.target=target; this.fieldType=fieldType; this.converter=converter;
  }
  public StructureModifier<T> withTarget(Object target) { return new StructureModifier<>(targetClass, target, fieldType, converter); }
  public <R> StructureModifier<R> withType(Class<?> type) { return new StructureModifier<>(targetClass, target, type, null); }
  public <R> StructureModifier<R> withType(Class<?> type, EquivalentConverter<R> converter) { return new StructureModifier<>(targetClass, target, type, converter); }
  public <R> StructureModifier<R> withConverter(EquivalentConverter<R> converter) { return new StructureModifier<>(targetClass, target, fieldType, converter); }

  private static Class<?> box(Class<?> c) {
    if (!c.isPrimitive()) return c;
    if(c==int.class)return Integer.class; if(c==long.class)return Long.class; if(c==double.class)return Double.class;
    if(c==float.class)return Float.class; if(c==short.class)return Short.class; if(c==byte.class)return Byte.class;
    if(c==boolean.class)return Boolean.class; if(c==char.class)return Character.class; return c;
  }
  private List<Field> fields() {
    List<Field> out=new ArrayList<>();
    for(Class<?> c=targetClass; c!=null && c!=Object.class; c=c.getSuperclass()) {
      String n=c.getName();
      if(n.equals("com.github.retrooper.packetevents.wrapper.PacketWrapper")) break;
      for(Field f:c.getDeclaredFields()) {
        if(Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) continue;
        if(fieldType!=Object.class && !box(fieldType).isAssignableFrom(box(f.getType())) && !box(f.getType()).isAssignableFrom(box(fieldType))) continue;
        try { f.setAccessible(true); } catch(Throwable ignored) {}
        out.add(f);
      }
    }
    return out;
  }
  @SuppressWarnings("unchecked") public T read(int index) {
    try {
      Field f=fields().get(index); Object value=f.get(target);
      return converter==null ? (T)value : converter.getSpecific(value);
    } catch(Exception e) { throw new FieldAccessException("Unable to read field " + index + " from " + targetClass.getName(), e); }
  }
  public T readSafely(int index) { try { return index>=0 && index<size() ? read(index) : null; } catch(RuntimeException e){ return null; } }
  public StructureModifier<T> write(int index, T value) {
    try {
      Field f=fields().get(index); Object generic=converter==null ? value : converter.getGeneric(value); f.set(target,generic); return this;
    } catch(Exception e) { throw new FieldAccessException("Unable to write field " + index + " on " + targetClass.getName(), e); }
  }
  public StructureModifier<T> writeSafely(int index, T value) { if(index>=0 && index<size()) write(index,value); return this; }
  public int size() { return fields().size(); }
  public List<T> getValues() { List<T> out=new ArrayList<>(); for(int i=0;i<size();i++) out.add(readSafely(i)); return out; }
  public List<Field> getFields() { return Collections.unmodifiableList(fields()); }
  public Field getField(int index) { return fields().get(index); }
}

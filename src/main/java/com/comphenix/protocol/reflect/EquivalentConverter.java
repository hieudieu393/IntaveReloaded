package com.comphenix.protocol.reflect;
public interface EquivalentConverter<T> {
  Object getGeneric(T specific);
  T getSpecific(Object generic);
  Class<T> getSpecificType();
}

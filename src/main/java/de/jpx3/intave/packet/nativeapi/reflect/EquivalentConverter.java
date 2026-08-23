package de.jpx3.intave.packet.nativeapi.reflect;
public interface EquivalentConverter<T> {
  Object getGeneric(T specific);
  T getSpecific(Object generic);
  Class<T> getSpecificType();
}

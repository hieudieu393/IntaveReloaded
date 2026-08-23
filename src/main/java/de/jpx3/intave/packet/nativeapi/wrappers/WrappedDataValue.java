package de.jpx3.intave.packet.nativeapi.wrappers;
public class WrappedDataValue { private final int index; private final Object serializer,value; public WrappedDataValue(int i,Object s,Object v){index=i;serializer=s;value=v;} public int getIndex(){return index;} public Object getSerializer(){return serializer;} public Object getRawValue(){return value;} public Object getValue(){return value;} }

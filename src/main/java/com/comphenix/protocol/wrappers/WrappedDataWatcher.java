package com.comphenix.protocol.wrappers;
import java.util.*;
public class WrappedDataWatcher {
  private final Map<Integer,Object> values=new HashMap<>(); private Object handle=this; private org.bukkit.entity.Entity entity;
  public WrappedDataWatcher(){} public WrappedDataWatcher(Object handle){this.handle=handle;}
  public void setObject(WrappedDataWatcherObject object,Object value){values.put(object.getIndex(),value);}
  public void setObject(int index,Object value){values.put(index,value);}
  public void setObject(int index,Object serializer,Object value){values.put(index,value);}
  public Object getObject(int index){return values.get(index);}
  public byte getByte(int index){Object v=values.get(index);return v instanceof Number?((Number)v).byteValue():0;}
  public Object getHandle(){return handle;} public org.bukkit.entity.Entity getEntity(){return entity;}
  public List<WrappedWatchableObject> getWatchableObjects(){List<WrappedWatchableObject> out=new ArrayList<>();for(Map.Entry<Integer,Object>e:values.entrySet())out.add(new WrappedWatchableObject(new WrappedDataWatcherObject(e.getKey(),Registry.get(e.getValue()==null?Object.class:e.getValue().getClass())),e.getValue()));return out;}
  public static final class Registry { public static Object get(Class<?> type){return type;} public static Object get(Class<?> type, boolean ignored){return type;} public static Object getChatComponentSerializer(boolean ignored){return Object.class;} public static Object getBlockPositionSerializer(boolean ignored){return Object.class;} public static Object getVectorSerializer(){return Object.class;} public static Object getItemStackSerializer(boolean ignored){return Object.class;} }
  public static class WrappedDataWatcherObject { private final int index; private final Object serializer; public WrappedDataWatcherObject(int i,Object s){index=i;serializer=s;} public int getIndex(){return index;} public Object getSerializer(){return serializer;} public Object getHandle(){return this;} }
}

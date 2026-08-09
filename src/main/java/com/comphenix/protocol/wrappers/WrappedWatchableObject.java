package com.comphenix.protocol.wrappers;
public class WrappedWatchableObject {
  private final WrappedDataWatcher.WrappedDataWatcherObject watcherObject; private Object value; private boolean dirty;
  public WrappedWatchableObject(WrappedDataWatcher.WrappedDataWatcherObject w,Object v){watcherObject=w;value=v;}
  public WrappedWatchableObject(int index,Object v){this(new WrappedDataWatcher.WrappedDataWatcherObject(index,v==null?Object.class:v.getClass()),v);}
  public int getIndex(){return watcherObject.getIndex();} public Object getRawValue(){return value;} public Object getValue(){return value;}
  public void setValue(Object value){this.value=value;} public void setDirtyState(boolean dirty){this.dirty=dirty;} public boolean getDirtyState(){return dirty;}
  public WrappedDataWatcher.WrappedDataWatcherObject getWatcherObject(){return watcherObject;}
}

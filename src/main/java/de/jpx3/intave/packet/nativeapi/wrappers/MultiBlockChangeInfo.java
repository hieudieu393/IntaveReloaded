package de.jpx3.intave.packet.nativeapi.wrappers;
public class MultiBlockChangeInfo {
  private final BlockPosition location; private final WrappedBlockData data;
  public MultiBlockChangeInfo(BlockPosition p, WrappedBlockData d){location=p;data=d;}
  public BlockPosition getLocation(){return location;} public int getAbsoluteX(){return location.getX();} public int getY(){return location.getY();} public int getAbsoluteZ(){return location.getZ();} public WrappedBlockData getData(){return data;}
}

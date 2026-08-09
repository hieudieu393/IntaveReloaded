package com.comphenix.protocol.wrappers;
import org.bukkit.util.Vector;
public class MovingObjectPositionBlock {
  private final Object handle; private BlockPosition blockPosition; private EnumWrappers.Direction direction;
  public MovingObjectPositionBlock(Object h){handle=h;} public Object getHandle(){return handle;}
  public BlockPosition getBlockPosition(){return blockPosition;} public void setBlockPosition(BlockPosition value){blockPosition=value;}
  public EnumWrappers.Direction getDirection(){return direction;} public void setDirection(EnumWrappers.Direction value){direction=value;}
  public Vector getPosVector(){return null;} public boolean isInsideBlock(){return false;}
}

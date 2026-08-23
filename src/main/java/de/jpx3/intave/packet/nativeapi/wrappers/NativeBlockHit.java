package de.jpx3.intave.packet.nativeapi.wrappers;
import org.bukkit.util.Vector;
public class NativeBlockHit {
  private final Object handle; private BlockPosition blockPosition; private EnumWrappers.Direction direction;
  public NativeBlockHit(Object h){handle=h;} public Object getHandle(){return handle;}
  public BlockPosition getBlockPosition(){return blockPosition;} public void setBlockPosition(BlockPosition value){blockPosition=value;}
  public EnumWrappers.Direction getDirection(){return direction;} public void setDirection(EnumWrappers.Direction value){direction=value;}
  public Vector getPosVector(){return null;} public boolean isInsideBlock(){return false;}
}

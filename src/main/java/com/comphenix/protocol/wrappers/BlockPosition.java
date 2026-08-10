package com.comphenix.protocol.wrappers;
import com.comphenix.protocol.reflect.EquivalentConverter;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
public class BlockPosition {
  private final int x,y,z;
  public BlockPosition(int x,int y,int z){this.x=x;this.y=y;this.z=z;}
  public BlockPosition(Vector vector){this(vector.getBlockX(),vector.getBlockY(),vector.getBlockZ());}
  public int getX(){return x;} public int getY(){return y;} public int getZ(){return z;}
  public Vector toVector(){return new Vector(x,y,z);}
  public Location toLocation(World world){return new Location(world,x,y,z);}
  public static EquivalentConverter<BlockPosition> getConverter(){return new EquivalentConverter<BlockPosition>(){
    public Object getGeneric(BlockPosition specific){return specific;}
    public BlockPosition getSpecific(Object generic){
      if(generic==null)return null;
      if(generic instanceof BlockPosition)return (BlockPosition)generic;
      if(generic instanceof Vector)return new BlockPosition((Vector)generic);
      try {
        java.lang.reflect.Method mx=generic.getClass().getMethod("getX"), my=generic.getClass().getMethod("getY"), mz=generic.getClass().getMethod("getZ");
        return new BlockPosition(((Number)mx.invoke(generic)).intValue(),((Number)my.invoke(generic)).intValue(),((Number)mz.invoke(generic)).intValue());
      } catch(Exception ignored){ return null; }
    }
    public Class<BlockPosition> getSpecificType(){return BlockPosition.class;}
  };}
  @Override public boolean equals(Object o){ if(!(o instanceof BlockPosition))return false; BlockPosition b=(BlockPosition)o;return x==b.x&&y==b.y&&z==b.z; }
  @Override public int hashCode(){return java.util.Objects.hash(x,y,z);} @Override public String toString(){return "BlockPosition{"+x+","+y+","+z+"}";}
}

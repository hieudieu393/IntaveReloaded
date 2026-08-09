package com.comphenix.protocol.wrappers;
public class BlockPosition {
  private final int x,y,z;
  public BlockPosition(int x,int y,int z){this.x=x;this.y=y;this.z=z;}
  public int getX(){return x;} public int getY(){return y;} public int getZ(){return z;}
  @Override public boolean equals(Object o){ if(!(o instanceof BlockPosition))return false; BlockPosition b=(BlockPosition)o;return x==b.x&&y==b.y&&z==b.z; }
  @Override public int hashCode(){return java.util.Objects.hash(x,y,z);} @Override public String toString(){return "BlockPosition{"+x+","+y+","+z+"}";}
}

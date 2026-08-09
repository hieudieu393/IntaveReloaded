package com.comphenix.protocol.wrappers;
import org.bukkit.util.Vector;
public class MovingObjectPositionBlock { private final Object handle; public MovingObjectPositionBlock(Object h){handle=h;} public Object getHandle(){return handle;} public BlockPosition getBlockPosition(){return null;} public EnumWrappers.Direction getDirection(){return null;} public Vector getPosVector(){return null;} public boolean isInsideBlock(){return false;} }

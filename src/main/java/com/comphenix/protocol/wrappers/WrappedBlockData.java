package com.comphenix.protocol.wrappers;
import org.bukkit.Material;
public class WrappedBlockData { private final Object handle; private final Material type; public WrappedBlockData(Object h){handle=h;type=null;} public WrappedBlockData(Material t){handle=t;type=t;} public static WrappedBlockData fromHandle(Object h){return new WrappedBlockData(h);} public static WrappedBlockData createData(Material m){return new WrappedBlockData(m);} public Object getHandle(){return handle;} public Material getType(){return type;} public int getData(){return 0;} }

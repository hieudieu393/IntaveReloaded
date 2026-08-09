package com.comphenix.protocol.wrappers;
public class MultiBlockChangeInfo { private final BlockPosition location; private final WrappedBlockData data; public MultiBlockChangeInfo(BlockPosition p, WrappedBlockData d){location=p;data=d;} public BlockPosition getLocation(){return location;} public WrappedBlockData getData(){return data;} }

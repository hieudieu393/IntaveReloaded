package com.comphenix.protocol.wrappers;
public final class WrappedSignedProperty { private final String name,value,signature; public WrappedSignedProperty(String n,String v,String s){name=n;value=v;signature=s;} public String getName(){return name;} public String getValue(){return value;} public String getSignature(){return signature;} }

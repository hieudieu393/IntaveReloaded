package com.comphenix.protocol.wrappers;
public class WrappedParticle<T> { private final Object particle; private final T data; private WrappedParticle(Object p,T d){particle=p;data=d;} public static <T> WrappedParticle<T> create(Object p,T d){return new WrappedParticle<>(p,d);} public Object getParticle(){return particle;} public T getData(){return data;} }

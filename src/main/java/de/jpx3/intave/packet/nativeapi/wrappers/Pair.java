package de.jpx3.intave.packet.nativeapi.wrappers;
public class Pair<A,B> { private A first; private B second; public Pair(A a,B b){first=a;second=b;} public A getFirst(){return first;} public B getSecond(){return second;} public void setFirst(A a){first=a;} public void setSecond(B b){second=b;} }

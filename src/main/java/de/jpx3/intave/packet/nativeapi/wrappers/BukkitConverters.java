package de.jpx3.intave.packet.nativeapi.wrappers;
import de.jpx3.intave.packet.nativeapi.reflect.EquivalentConverter;
import org.bukkit.util.Vector;
import java.util.*;
public final class BukkitConverters {
  private BukkitConverters(){}
  public static EquivalentConverter<Vector> getVectorConverter(){return passthrough(Vector.class);}
  public static EquivalentConverter<WrappedGameProfile> getWrappedGameProfileConverter(){return new EquivalentConverter<WrappedGameProfile>(){public Object getGeneric(WrappedGameProfile s){return s==null?null:s.getHandle();}public WrappedGameProfile getSpecific(Object o){return o==null?null:new WrappedGameProfile(o);}public Class<WrappedGameProfile> getSpecificType(){return WrappedGameProfile.class;}};}
  public static EquivalentConverter<WrappedChatComponent> getWrappedChatComponentConverter(){return new EquivalentConverter<WrappedChatComponent>(){public Object getGeneric(WrappedChatComponent s){return s==null?null:s.getHandle();}public WrappedChatComponent getSpecific(Object o){return o==null?null:new WrappedChatComponent(o);}public Class<WrappedChatComponent> getSpecificType(){return WrappedChatComponent.class;}};}
  public static <T> EquivalentConverter<List<T>> getListConverter(EquivalentConverter<T> item){return new EquivalentConverter<List<T>>(){public Object getGeneric(List<T> s){if(s==null)return null;List<Object>o=new ArrayList<>();for(T x:s)o.add(item.getGeneric(x));return o;}public List<T> getSpecific(Object g){if(!(g instanceof Iterable))return Collections.emptyList();List<T>o=new ArrayList<>();for(Object x:(Iterable<?>)g)o.add(item.getSpecific(x));return o;}@SuppressWarnings("unchecked")public Class<List<T>> getSpecificType(){return (Class)List.class;}};}
  public static <T> EquivalentConverter<T> passthrough(Class<T> c){return new EquivalentConverter<T>(){public Object getGeneric(T s){return s;}public T getSpecific(Object o){return c.isInstance(o)?c.cast(o):null;}public Class<T> getSpecificType(){return c;}};}
}

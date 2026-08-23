package de.jpx3.intave.module.linker.packet;

import com.google.common.base.Objects;
import org.jetbrains.annotations.NotNull;

import java.util.*;

// SortedCopyOnWriteArray-List
final class SCOWAList<T extends Comparable<T>> implements Collection<T> {
  private volatile List<T> list;

  public SCOWAList() {
    this.list = new ArrayList<>();
  }

  public SCOWAList(Collection<T> wrapped) {
    this.list = new ArrayList<>(wrapped);
  }

  public SCOWAList(Collection<T> wrapped, boolean sort) {
    this.list = new ArrayList<>(wrapped);
    if (sort) {
      Collections.sort(this.list);
    }
  }

  @Override
  public synchronized boolean add(T value) {
    if (value == null) {
      throw new IllegalArgumentException("value cannot be NULL");
    }

    List<T> copy = new ArrayList<>(list.size() + 1);
    boolean inserted = false;
    for (T element : list) {
      if (!inserted && value.compareTo(element) < 0) {
        copy.add(value);
        inserted = true;
      }
      copy.add(element);
    }
    if (!inserted) {
      copy.add(value);
    }
    list = copy;
    return true;
  }

  @Override
  public synchronized boolean addAll(Collection<? extends T> values) {
    if (values == null) {
      throw new IllegalArgumentException("values cannot be NULL");
    }
    if (values.isEmpty()) {
      return false;
    }

    List<T> copy = new ArrayList<>(list);
    boolean changed = copy.addAll(values);
    if (changed) {
      Collections.sort(copy);
      list = copy;
    }
    return changed;
  }

  @Override
  public synchronized boolean remove(Object value) {
    List<T> copy = new ArrayList<>();
    boolean changed = false;
    for (T element : list) {
      if (!Objects.equal(value, element)) {
        copy.add(element);
      } else {
        changed = true;
      }
    }
    if (changed) {
      list = copy;
    }
    return changed;
  }

  @Override
  public synchronized boolean removeAll(Collection<?> values) {
    if (values == null) {
      throw new IllegalArgumentException("values cannot be NULL");
    }
    if (values.isEmpty()) {
      return false;
    }

    List<T> copy = new ArrayList<>(list);
    boolean changed = copy.removeAll(values);
    if (changed) {
      list = copy;
    }
    return changed;
  }

  @Override
  public synchronized boolean retainAll(Collection<?> values) {
    if (values == null) {
      throw new IllegalArgumentException("values cannot be NULL");
    }

    List<T> copy = new ArrayList<>(list);
    boolean changed = copy.retainAll(values);
    if (changed) {
      list = copy;
    }
    return changed;
  }

  public synchronized void remove(int index) {
    List<T> copy = new ArrayList<>(list);
    copy.remove(index);
    list = copy;
  }

  public T get(int index) {
    return list.get(index);
  }

  @Override
  public int size() {
    return list.size();
  }

  @Override
  public @NotNull Iterator<T> iterator() {
    return Collections.unmodifiableList(list).iterator();
  }

  @Override
  public synchronized void clear() {
    if (!list.isEmpty()) {
      list = new ArrayList<>();
    }
  }

  @Override
  public boolean contains(Object value) {
    return list.contains(value);
  }

  @Override
  public boolean containsAll(Collection<?> values) {
    return list.containsAll(values);
  }

  @Override
  public boolean isEmpty() {
    return list.isEmpty();
  }

  @Override
  public Object[] toArray() {
    return list.toArray();
  }

  @Override
  public <X> X[] toArray(X[] a) {
    return list.toArray(a);
  }

  @Override
  public String toString() {
    return list.toString();
  }
}

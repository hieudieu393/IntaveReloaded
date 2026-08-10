/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.share;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public final class LazyInitMap<K, V> implements Map<K, V> {

	private Map<K, V> delegate;
	private final Supplier<? extends Map<K, V>> factory;

	private LazyInitMap(Supplier<? extends Map<K, V>> factory) {
		this.factory = Objects.requireNonNull(factory, "factory");
	}

	private Map<K, V> delegate() {
		if (delegate == null) {
			delegate = Objects.requireNonNull(
				factory.get(),
				"factory returned null"
			);
		}

		return delegate;
	}

	@Override
	public int size() {
		return delegate().size();
	}

	@Override
	public boolean isEmpty() {
		return delegate().isEmpty();
	}

	@Override
	public boolean containsKey(Object key) {
		return delegate().containsKey(key);
	}

	@Override
	public boolean containsValue(Object value) {
		return delegate().containsValue(value);
	}

	@Override
	public V get(Object key) {
		return delegate().get(key);
	}

	@Override
	public V put(K key, V value) {
		return delegate().put(key, value);
	}

	@Override
	public V remove(Object key) {
		return delegate().remove(key);
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> map) {
		delegate().putAll(map);
	}

	@Override
	public void clear() {
		delegate().clear();
	}

	@Override
	public Set<K> keySet() {
		return delegate().keySet();
	}

	@Override
	public Collection<V> values() {
		return delegate().values();
	}

	@Override
	public Set<Entry<K, V>> entrySet() {
		return delegate().entrySet();
	}

	// Delegate default Map methods as well, preserving the underlying
	// map's specialized implementations and behavior.

	@Override
	public V getOrDefault(Object key, V defaultValue) {
		return delegate().getOrDefault(key, defaultValue);
	}

	@Override
	public void forEach(BiConsumer<? super K, ? super V> action) {
		delegate().forEach(action);
	}

	@Override
	public void replaceAll(
		BiFunction<? super K, ? super V, ? extends V> function) {
		delegate().replaceAll(function);
	}

	@Override
	public V putIfAbsent(K key, V value) {
		return delegate().putIfAbsent(key, value);
	}

	@Override
	public boolean remove(Object key, Object value) {
		return delegate().remove(key, value);
	}

	@Override
	public boolean replace(K key, V oldValue, V newValue) {
		return delegate().replace(key, oldValue, newValue);
	}

	@Override
	public V replace(K key, V value) {
		return delegate().replace(key, value);
	}

	@Override
	public V computeIfAbsent(
		K key,
		Function<? super K, ? extends V> mappingFunction) {
		return delegate().computeIfAbsent(key, mappingFunction);
	}

	@Override
	public V computeIfPresent(
		K key,
		BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
		return delegate().computeIfPresent(key, remappingFunction);
	}

	@Override
	public V compute(
		K key,
		BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
		return delegate().compute(key, remappingFunction);
	}

	@Override
	public V merge(
		K key,
		V value,
		BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
		return delegate().merge(key, value, remappingFunction);
	}

	@Override
	public boolean equals(Object other) {
		return other == this || delegate().equals(other);
	}

	@Override
	public int hashCode() {
		return delegate().hashCode();
	}

	@Override
	public String toString() {
		return delegate().toString();
	}

	public static <K, V> LazyInitMap<K, V> of(Supplier<? extends Map<K, V>> factory) {
		return new LazyInitMap<>(factory);
	}
}
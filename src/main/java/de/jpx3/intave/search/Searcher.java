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

package de.jpx3.intave.search;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public final class Searcher<I, T> {
	private final List<SearchBrancher<I, T>> branchers;
	private final Function<I, T> initial;

	public Searcher(List<SearchBrancher<I, T>> branchers, Function<I, T> initial) {
		this.branchers = branchers;
		this.initial = initial;
	}

	private static final class ReusableCache<T> {
		private final Set<T> alpha = new HashSet<>();
		private final Set<T> beta = new HashSet<>();
	}

	private final ThreadLocal<ReusableCache<T>> cachedBuffers = ThreadLocal.withInitial(ReusableCache::new);

	public Set<T> searchConfigurationsFor(I input) {
		ReusableCache<T> buffers = cachedBuffers.get();
		Set<T> current = buffers.alpha;
		Set<T> next = buffers.beta;

		current.clear();
		next.clear();
		current.add(initial.apply(input));

		for (SearchBrancher<I, T> brancher : branchers) {
			next.clear();
			for (T configuration : current) {
				brancher.branch(input, configuration, next);
			}
			if (next.isEmpty()) {
				throw new IllegalStateException("Brancher " + brancher + " produced no results for input " + input + " and result " + current);
			}
			Set<T> temporary = current;
			current = next;
			next = temporary;
		}
		return Collections.unmodifiableSet(current);
	}
}

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

import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearcherTest {

	@Test
	public void testExample() {
		class ExampleBrancher extends SearchBrancher<Object, String> {
			@Override
			public void branch(Object input, String inputBranch, Collection<String> outputBranches) {
				outputBranches.add(inputBranch + "A");
				outputBranches.add(inputBranch + "B");
			}
		}
		Searcher<Object, String> searcher = new Searcher<>(
			List.of(
				new ExampleBrancher(),
				new ExampleBrancher(),
				new ExampleBrancher()
			),
			_ -> ""
		);
		Set<String> configs = searcher.searchConfigurationsFor(null);
		assertEquals(Set.of("AAA", "AAB", "ABA", "ABB", "BAA", "BAB", "BBA", "BBB"), configs);
	}

	@Test
	public void testDuplicateBranchesAreDiscarded() {
		Searcher<Object, String> searcher = new Searcher<>(
			List.of(new SearchBrancher<>() {
				@Override
				public void branch(Object input, String inputBranch, Collection<String> outputBranches) {
					outputBranches.add(inputBranch + "A");
					outputBranches.add(inputBranch + "A");
					outputBranches.add(inputBranch + "B");
				}
			}),
			_ -> ""
		);

		Set<String> configs = searcher.searchConfigurationsFor(null);
		assertEquals(Set.of("A", "B"), configs);
	}
}

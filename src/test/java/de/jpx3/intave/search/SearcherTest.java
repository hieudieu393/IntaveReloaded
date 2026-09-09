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
import static org.junit.jupiter.api.Assertions.assertSame;

class SearcherTest {

	@Test
	void collidingConfigurationsAreDeduplicatedAndClearedBetweenSearches() {
		record Configuration(int value) {
			@Override
			public int hashCode() {
				return 1;
			}
		}
		Configuration[] originals = new Configuration[64];
		for (int i = 0; i < originals.length; i++) {
			originals[i] = new Configuration(i);
		}
		Searcher<Integer, Configuration> searcher = new Searcher<>(List.of(
			new SearchBrancher<Integer, Configuration>() {
				@Override
				public void branch(Integer count, Configuration configuration, Collection<Configuration> output) {
					for (int i = 0; i < count; i++) {
						output.add(originals[i]);
						output.add(new Configuration(i));
					}
				}
			},
			new SearchBrancher<Integer, Configuration>() {
				@Override
				public void branch(Integer count, Configuration configuration, Collection<Configuration> output) {
					output.add(configuration);
				}
			}
		), _ -> originals[0]);

		for (int count : new int[]{64, 3, 64}) {
			Set<Configuration> result = searcher.searchConfigurationsFor(count);
			assertEquals(Set.copyOf(List.of(originals).subList(0, count)), result);
			for (Configuration configuration : result) {
				assertSame(originals[configuration.value()], configuration);
			}
		}
	}

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

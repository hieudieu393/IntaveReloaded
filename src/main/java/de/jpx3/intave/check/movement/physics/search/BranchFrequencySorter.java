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

package de.jpx3.intave.check.movement.physics.search;

import de.jpx3.intave.check.movement.physics.branch.MovementSearchBranch;
import it.unimi.dsi.fastutil.longs.Long2LongMap;

import java.util.Arrays;
import java.util.Comparator;

final class BranchFrequencySorter {
  private static final Comparator<Entry> BY_FREQUENCY = (left, right) ->
    Long.compare(right.frequency, left.frequency);

  private Entry[] entries = new Entry[0];

  void sort(MovementSearchBranch[] branches, Long2LongMap frequencies) {
    int size = branches.length;
    if (size < 2 || frequencies.isEmpty()) {
      return;
    }
    if (entries.length < size) {
      int previousSize = entries.length;
      entries = Arrays.copyOf(entries, size);
      for (int i = previousSize; i < size; i++) {
        entries[i] = new Entry();
      }
    }
    boolean alreadySorted = true;
    long previousFrequency = Long.MAX_VALUE;
    for (int i = 0; i < size; i++) {
      Entry entry = entries[i];
      entry.branch = branches[i];
      entry.frequency = frequencies.get(branches[i].frequencyKey());
      if (entry.frequency > previousFrequency) {
        alreadySorted = false;
      }
      previousFrequency = entry.frequency;
    }

    // Stable sorting keeps the input order when frequencies are equal.
    if (!alreadySorted) {
      Arrays.sort(entries, 0, size, BY_FREQUENCY);
    }
    for (int i = 0; i < size; i++) {
      Entry entry = entries[i];
      branches[i] = entry.branch;
      entry.branch = null;
    }
  }

  private static final class Entry {
    private MovementSearchBranch branch;
    private long frequency;
  }
}

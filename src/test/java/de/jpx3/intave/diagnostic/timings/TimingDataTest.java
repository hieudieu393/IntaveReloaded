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

package de.jpx3.intave.diagnostic.timings;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TimingDataTest {
  @Test
  void reportsZeroWithoutSamples() {
    assertEquals(0, new TimingData().p99Duration());
  }

  @Test
  void reportsNearestRankP99() {
    TimingData timingData = new TimingData();
    for (int duration = 1; duration <= 100; duration++) {
      timingData.addDuration(duration);
    }

    assertEquals(99, timingData.p99Duration());
  }

  @Test
  void retainsLifetimeDistribution() {
    TimingData timingData = new TimingData();
    for (int repetition = 0; repetition < 200; repetition++) {
      for (int duration = 1; duration <= 100; duration++) {
        timingData.addDuration(duration);
      }
    }

    assertEquals(99, timingData.p99Duration());
  }

  @Test
  void roundsUpWithinHistogramPrecision() {
    TimingData timingData = new TimingData();
    timingData.addDuration(1_000_000);

    long p99Duration = timingData.p99Duration();
    assertTrue(p99Duration >= 1_000_000);
    assertTrue(p99Duration < 1_032_000);
  }

  @Test
  void cloneHasIndependentHistogram() {
    TimingData original = new TimingData();
    original.addDuration(1);
    TimingData clone = original.clone();

    original.addDuration(1_000_000);

    assertEquals(1, clone.p99Duration());
  }

  @Test
  void exposesEmptyHistogramWithoutSamples() {
    assertTrue(new TimingData().durationHistogram(10).isEmpty());
  }

  @Test
  void aggregatesHistogramForChatWithoutLosingSamples() {
    TimingData timingData = new TimingData();
    for (int duration = 1; duration <= 100; duration++) {
      timingData.addDuration(duration);
    }

    List<TimingData.DurationBucket> histogram = timingData.durationHistogram(8);

    assertEquals(8, histogram.size());
    assertEquals(100, histogram.stream().mapToLong(TimingData.DurationBucket::samples).sum());
    assertTrue(histogram.get(0).lowerBoundNanos() <= 1L);
    assertTrue(histogram.get(histogram.size() - 1).upperBoundNanos() >= 100L);
    for (int i = 1; i < histogram.size(); i++) {
      assertEquals(histogram.get(i - 1).upperBoundNanos() + 1L, histogram.get(i).lowerBoundNanos());
    }
  }
}

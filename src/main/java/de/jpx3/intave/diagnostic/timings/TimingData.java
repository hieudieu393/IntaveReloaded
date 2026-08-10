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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLongArray;

public class TimingData implements Cloneable {
  private static final int DURATION_BUCKET_PRECISION_BITS = 5;
  private static final int DURATION_BUCKETS_PER_POWER_OF_TWO = 1 << DURATION_BUCKET_PRECISION_BITS;
  private static final int DURATION_BUCKET_COUNT =
    (Long.SIZE - DURATION_BUCKET_PRECISION_BITS) * DURATION_BUCKETS_PER_POWER_OF_TWO;

  private volatile long totalDuration;
  private volatile long calls;
  private AtomicLongArray durationHistogram = new AtomicLongArray(DURATION_BUCKET_COUNT);

  public void addDuration(long durationToAdd) {
    setTotalDuration(totalDuration() + durationToAdd);
    durationHistogram.incrementAndGet(durationBucketIndex(durationToAdd));
  }

  public void increaseCallCount() {
    setCalls(calls() + 1);
  }

  public long totalDuration() {
    return totalDuration;
  }

  private void setTotalDuration(long totalDuration) {
    this.totalDuration = totalDuration;
  }

  public long calls() {
    return calls;
  }

  public long p99Duration() {
    long[] histogramSnapshot = durationHistogramSnapshot();
    long sampleCount = 0;
    for (long bucketSize : histogramSnapshot) {
      sampleCount += bucketSize;
    }
    if (sampleCount == 0L) {
      return 0;
    }

    long targetRank = (long) Math.ceil(sampleCount * 0.99d);
    long seenSamples = 0;
    for (int i = 0; i < histogramSnapshot.length; i++) {
      seenSamples += histogramSnapshot[i];
      if (seenSamples >= targetRank) {
        return durationBucketUpperBound(i);
      }
    }
    return durationBucketUpperBound(DURATION_BUCKET_COUNT - 1);
  }

  public List<DurationBucket> durationHistogram(int maximumBucketCount) {
    if (maximumBucketCount < 1) {
      throw new IllegalArgumentException("maximumBucketCount must be positive");
    }

    long[] histogramSnapshot = durationHistogramSnapshot();
    int firstBucket = 0;
    while (firstBucket < histogramSnapshot.length && histogramSnapshot[firstBucket] == 0L) {
      firstBucket++;
    }
    if (firstBucket == histogramSnapshot.length) {
      return Collections.emptyList();
    }

    int lastBucket = histogramSnapshot.length - 1;
    while (histogramSnapshot[lastBucket] == 0L) {
      lastBucket--;
    }

    int sourceBucketCount = lastBucket - firstBucket + 1;
    int outputBucketCount = Math.min(maximumBucketCount, sourceBucketCount);
    List<DurationBucket> buckets = new ArrayList<>(outputBucketCount);
    for (int outputBucket = 0; outputBucket < outputBucketCount; outputBucket++) {
      int bucketStart = firstBucket + outputBucket * sourceBucketCount / outputBucketCount;
      int bucketEnd = firstBucket + (outputBucket + 1) * sourceBucketCount / outputBucketCount - 1;
      long samples = 0L;
      for (int sourceBucket = bucketStart; sourceBucket <= bucketEnd; sourceBucket++) {
        samples += histogramSnapshot[sourceBucket];
      }
      long lowerBound = bucketStart == 0 ? 0L : durationBucketUpperBound(bucketStart - 1) + 1L;
      buckets.add(new DurationBucket(lowerBound, durationBucketUpperBound(bucketEnd), samples));
    }
    return buckets;
  }

  private long[] durationHistogramSnapshot() {
    long[] snapshot = new long[DURATION_BUCKET_COUNT];
    for (int i = 0; i < snapshot.length; i++) {
      snapshot[i] = durationHistogram.get(i);
    }
    return snapshot;
  }

  private static int durationBucketIndex(long duration) {
    if (duration <= 0L) {
      return 0;
    }
    int powerOfTwo = 63 - Long.numberOfLeadingZeros(duration);
    if (powerOfTwo <= DURATION_BUCKET_PRECISION_BITS) {
      return (int) duration;
    }
    int shift = powerOfTwo - DURATION_BUCKET_PRECISION_BITS;
    return shift * DURATION_BUCKETS_PER_POWER_OF_TWO + (int) (duration >>> shift);
  }

  private static long durationBucketUpperBound(int bucketIndex) {
    if (bucketIndex < DURATION_BUCKETS_PER_POWER_OF_TWO * 2) {
      return bucketIndex;
    }
    int powerOfTwo = bucketIndex / DURATION_BUCKETS_PER_POWER_OF_TWO
      + DURATION_BUCKET_PRECISION_BITS - 1;
    int shift = powerOfTwo - DURATION_BUCKET_PRECISION_BITS;
    int mantissa = bucketIndex - shift * DURATION_BUCKETS_PER_POWER_OF_TWO;
    return ((long) (mantissa + 1) << shift) - 1L;
  }

  private void setCalls(long calls) {
    this.calls = calls;
  }

  public static final class DurationBucket {
    private final long lowerBoundNanos;
    private final long upperBoundNanos;
    private final long samples;

    private DurationBucket(long lowerBoundNanos, long upperBoundNanos, long samples) {
      this.lowerBoundNanos = lowerBoundNanos;
      this.upperBoundNanos = upperBoundNanos;
      this.samples = samples;
    }

    public long lowerBoundNanos() {
      return lowerBoundNanos;
    }

    public long upperBoundNanos() {
      return upperBoundNanos;
    }

    public long samples() {
      return samples;
    }
  }

  @Override
  public TimingData clone() {
    try {
      TimingData clone = (TimingData) super.clone();
      clone.durationHistogram = new AtomicLongArray(DURATION_BUCKET_COUNT);
      for (int i = 0; i < DURATION_BUCKET_COUNT; i++) {
        clone.durationHistogram.set(i, durationHistogram.get(i));
      }
      return clone;
    } catch (CloneNotSupportedException e) {
      throw new Error(e);
    }
  }
}

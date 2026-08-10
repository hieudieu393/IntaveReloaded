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

import de.jpx3.intave.IntaveControl;
import org.bukkit.ChatColor;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Timing implements Cloneable, Comparable<Timing> {

  private final String timingName;
  private final String parentName;
  private TimingType timingType = TimingType.BASE;
  private final TimingData totalTimingData = new TimingData();

  private final ThreadLocal<TimingContainer> state = ThreadLocal.withInitial(TimingContainer::new);

  private Timing(String timingName, String parentName) {
    this.timingName = timingName;
    this.parentName = parentName;
  }

  public void start() {
    if (IntaveControl.USE_TIMINGS) {
      // start from sync start
      state.get().lastStart = now();
    }
  }

  public void stop() {
    if (IntaveControl.USE_TIMINGS) {
      // end from before sync
      long now = now();
      totalTimingData.addDuration(now - state.get().lastStart);
      totalTimingData.increaseCallCount();
    }
  }

  public String parentName() {
    return parentName;
  }

  public Timing parent() {
    return parentName == null ? null : Timings.lookupTimingByName(parentName);
  }

  public String name() {
    return timingName;
  }

  public String coloredName() {
    String name = name();
    List<String> path = Arrays.stream(name.split("/")).collect(Collectors.toList());
    for (int i = 0, pathSize = path.size(); i < pathSize; i++) {
      String pathElement = path.get(i);
      ChatColor correspCC = Timings.COLOR_CODE_NAMESPACE.get(pathElement);
      if (correspCC != null) {
        pathElement = correspCC + pathElement + ChatColor.WHITE;
      }
      path.set(i, pathElement);
    }
    String outputString = path.stream().map(s -> s + "/").collect(Collectors.joining());
    return outputString.substring(0, outputString.length() - 1);
  }

  public void specifyAsBukkitEventTiming() {
    timingType = TimingType.BUKKIT_EVENT;
  }

  public boolean isBukkitEventTiming() {
    return timingType == TimingType.BUKKIT_EVENT;
  }

  public void specifyAsPacketEventTiming() {
    timingType = TimingType.PACKET_EVENT;
  }

  public boolean isPacketEventTiming() {
    return timingType == TimingType.PACKET_EVENT;
  }

  public long totalDurationNanos() {
    return totalTimingData.totalDuration();
  }

  public double totalDurationMillis() {
    return totalDurationNanos() / 1000000d;
  }

  public long recordedCalls() {
    return totalTimingData.calls();
  }

  public double averageCallDurationInNanos() {
    return totalDurationNanos() / Math.max(1d, recordedCalls());
  }

  public double averageCallDurationInMillis() {
    return (totalDurationMillis()) / (double) Math.max(1, recordedCalls());
  }

  public long p99CallDurationInNanos() {
    return totalTimingData.p99Duration();
  }

  public double p99CallDurationInMillis() {
    return p99CallDurationInNanos() / 1000000d;
  }

  public List<TimingData.DurationBucket> callDurationHistogram(int maximumBucketCount) {
    return totalTimingData.durationHistogram(maximumBucketCount);
  }

  public double durationInTicks() {
    return averageCallDurationInMillis() / 50;
  }

  @Override
  public int compareTo(Timing o) {
    return Long.compare(o.totalDurationNanos(), totalDurationNanos());
  }

  @Override
  public Timing clone() {
    try {
      return (Timing) super.clone();
    } catch (CloneNotSupportedException e) {
      throw new IllegalStateException(e);
    }
  }

  private static long now() {
    return System.nanoTime();
  }

  private static final class TimingContainer {
    private long lastStart;
  }

  static Timing of(String name) {
    Timing timing = new Timing(name, null);
    Timings.addTiming(timing);
    return timing;
  }

  static Timing of(String name, String parentName) {
    Timing timing = new Timing(name, parentName);
    Timings.addTiming(timing);
    return timing;
  }

  public enum TimingType {
    BASE,
    BUKKIT_EVENT,
    PACKET_EVENT
  }
}

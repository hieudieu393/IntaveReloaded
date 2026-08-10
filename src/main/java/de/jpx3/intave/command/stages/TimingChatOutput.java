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

package de.jpx3.intave.command.stages;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.diagnostic.timings.Timing;
import de.jpx3.intave.diagnostic.timings.TimingData;
import de.jpx3.intave.diagnostic.timings.Timings;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.List;

import static de.jpx3.intave.math.MathHelper.formatDouble;

final class TimingChatOutput {
  private static final int HISTOGRAM_BUCKET_COUNT = 10;
  private static final int HISTOGRAM_BAR_WIDTH = 12;

  private TimingChatOutput() {
  }

  static void sendSelectableTiming(Player player, Timing timing, String message, String histogramCommand) {
    TextComponent component = new TextComponent(ChatColor.translateAlternateColorCodes('&', message));
    component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, histogramCommand + timing.name()));
    component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponent[]{
      new TextComponent(ChatColor.YELLOW + "Click to view the duration histogram")
    }));
    player.spigot().sendMessage(component);
  }

  static void sendHistogram(Player player, String[] timingNameParts) {
    String timingName = String.join(" ", timingNameParts);
    Timing timing = Timings.lookupTimingByName(timingName);
    if (timing == null) {
      player.sendMessage(IntavePlugin.prefix() + ChatColor.RED + "Unknown timing " + timingName);
      return;
    }

    List<TimingData.DurationBucket> buckets = timing.callDurationHistogram(HISTOGRAM_BUCKET_COUNT);
    player.sendMessage(ChatColor.DARK_GRAY + "--- " + timing.coloredName() + ChatColor.DARK_GRAY + " duration histogram ---");
    if (buckets.isEmpty()) {
      player.sendMessage(ChatColor.GRAY + "No duration samples have been recorded yet.");
      return;
    }

    long sampleCount = 0L;
    long largestBucket = 0L;
    for (TimingData.DurationBucket bucket : buckets) {
      sampleCount += bucket.samples();
      largestBucket = Math.max(largestBucket, bucket.samples());
    }

    String[] rangeLabels = new String[buckets.size()];
    int rangeColumnWidth = 0;
    for (int i = 0; i < buckets.size(); i++) {
      rangeLabels[i] = formatRange(buckets.get(i));
      rangeColumnWidth = Math.max(rangeColumnWidth, rangeLabels[i].length());
    }

    for (int i = 0; i < buckets.size(); i++) {
      TimingData.DurationBucket bucket = buckets.get(i);
      int barLength = bucket.samples() == 0L
        ? 0
        : Math.max(1, (int) Math.round(bucket.samples() * HISTOGRAM_BAR_WIDTH / (double) largestBucket));
      double share = bucket.samples() * 100d / sampleCount;
      String bar = barLength == 0 ? ChatColor.DARK_GRAY + "-" : ChatColor.AQUA + repeat('\u2588', barLength);
      player.sendMessage(
        ChatColor.GRAY + padRight(rangeLabels[i], rangeColumnWidth) + ChatColor.DARK_GRAY + " | " + bar + " " +
          ChatColor.GRAY + bucket.samples() + " (" + formatDouble(share, 1) + "%)"
      );
    }

    player.sendMessage(
      ChatColor.DARK_GRAY + "Samples " + ChatColor.GRAY + sampleCount +
        ChatColor.DARK_GRAY + " | avg " + ChatColor.GRAY + formatDuration((long) timing.averageCallDurationInNanos()) +
        ChatColor.DARK_GRAY + " | p99 " + ChatColor.GRAY + formatDuration(timing.p99CallDurationInNanos())
    );
  }

  private static String formatRange(TimingData.DurationBucket bucket) {
    if (bucket.lowerBoundNanos() == bucket.upperBoundNanos()) {
      return formatDuration(bucket.upperBoundNanos());
    }
    return formatDuration(bucket.lowerBoundNanos()) + "-" + formatDuration(bucket.upperBoundNanos());
  }

  private static String formatDuration(long nanos) {
    if (nanos < 1_000L) {
      return nanos + "ns";
    }
    if (nanos < 1_000_000L) {
      return formatDouble(nanos / 1_000d, 2) + "us";
    }
    if (nanos < 1_000_000_000L) {
      return formatDouble(nanos / 1_000_000d, 2) + "ms";
    }
    return formatDouble(nanos / 1_000_000_000d, 2) + "s";
  }

  private static String repeat(char character, int count) {
    StringBuilder output = new StringBuilder(count);
    for (int i = 0; i < count; i++) {
      output.append(character);
    }
    return output.toString();
  }

  private static String padRight(String value, int width) {
    StringBuilder output = new StringBuilder(width);
    output.append(value);
    while (output.length() < width) {
      output.append(' ');
    }
    return output.toString();
  }
}

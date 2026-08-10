package com.comphenix.protocol.utility;

/** Minimal version value kept for Intave's existing version comparisons after removing ProtocolLib. */
public final class MinecraftVersion implements Comparable<MinecraftVersion> {
  public static final MinecraftVersion v1_21_4 = new MinecraftVersion(1, 21, 4);

  private static MinecraftVersion currentVersion = new MinecraftVersion(0, 0, 0);
  private final int major, minor, build;

  public MinecraftVersion(int major, int minor, int build) {
    this.major = major;
    this.minor = minor;
    this.build = build;
  }

  public static MinecraftVersion currentVersion() { return currentVersion; }
  public static void setCurrentVersion(MinecraftVersion version) { currentVersion = version; }
  public boolean atOrAbove(MinecraftVersion other) { return compareTo(other) >= 0; }

  @Override
  public int compareTo(MinecraftVersion other) {
    int result = Integer.compare(major, other.major);
    if (result != 0) return result;
    result = Integer.compare(minor, other.minor);
    return result != 0 ? result : Integer.compare(build, other.build);
  }

  @Override
  public String toString() { return major + "." + minor + "." + build; }
}

package com.comphenix.protocol.utility;

/** Minimal version value kept for Intave's existing version comparisons after removing PacketEvents. */
public final class MinecraftVersion implements Comparable<MinecraftVersion> {
  private static MinecraftVersion currentVersion = new MinecraftVersion(0, 0, 0);
  private final int major, minor, build;
  public MinecraftVersion(int major, int minor, int build) { this.major=major; this.minor=minor; this.build=build; }
  public static MinecraftVersion currentVersion() { return currentVersion; }
  public static void setCurrentVersion(MinecraftVersion version) { currentVersion=version; }
  public boolean atOrAbove(MinecraftVersion other) { return compareTo(other) >= 0; }
  @Override public int compareTo(MinecraftVersion o) { int c=Integer.compare(major,o.major); if(c!=0)return c; c=Integer.compare(minor,o.minor); return c!=0?c:Integer.compare(build,o.build); }
  @Override public String toString(){return major+"."+minor+"."+build;}
}

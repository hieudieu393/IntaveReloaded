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

package de.jpx3.intave.module.patcher;

import com.google.common.collect.Sets;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.klass.rewrite.PatchyLoadingInjector;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.world.chunk.ChunkProviderServerAccess;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.world.WorldInitEvent;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Iterator;

public final class ChunkAccessPatcher extends Module {
  private static final boolean ENABLED = !MinecraftVersions.VER1_14_0.atOrAbove();

  static {
    if (ENABLED) {
      ClassLoader classLoader = IntavePlugin.class.getClassLoader();
      PatchyLoadingInjector.loadUnloadedClassPatched(classLoader, "de.jpx3.intave.module.patcher.SynchronizedBukkitLongHashSet");
    }
  }

  @Override
  public void enable() { Bukkit.getWorlds().forEach(this::patchWorld); }

  @BukkitEventSubscription
  public void worldInit(WorldInitEvent event) { patchWorld(event.getWorld()); }

  private static boolean failedDSIFirstPatch = false;

  public void patchWorld(World world) {
    if (!ENABLED) return;
    String patchName = "Unknown";
    try {
      Field unloadQueueField = unloadQueueField();
      if (unloadQueueField == null) return;
      if (!unloadQueueField.isAccessible()) unloadQueueField.setAccessible(true);
      String className = unloadQueueField.getType().getName();
      Object chunkProviderServer = ChunkProviderServerAccess.chunkProviderServerOf(world);
      Object unloadQueue = unloadQueueField.get(chunkProviderServer);
      Iterator<Long> iterator;
      try { iterator = (Iterator<Long>) unloadQueue.getClass().getMethod("iterator").invoke(unloadQueue); }
      catch (Exception exception) { iterator = Collections.emptyIterator(); }
      if (className.contains("dsi.fastutil.longs")) {
        if (className.endsWith("LongArraySet")) {
          patchName = "s(dsi/ls)";
          SynchronizedLongArraySet newQueue = new SynchronizedLongArraySet();
          unloadQueueField.set(chunkProviderServer, newQueue);
          iterator.forEachRemaining(newQueue::add);
        } else {
          patchName = "s(dsi/lhs)";
          try {
            if (failedDSIFirstPatch) throw new Exception("Failed before");
            SynchronizedDSILongHashSet newQueue = new SynchronizedDSILongHashSet();
            unloadQueueField.set(chunkProviderServer, newQueue);
            iterator.forEachRemaining(newQueue::add);
          } catch (Throwable ignored) {
            if (!failedDSIFirstPatch) {
              IntaveLogger.logger().info("Using alternative patch for unload queue for " + className);
              failedDSIFirstPatch = true;
            }
            LongSet queue = (LongSet) unloadQueue;
            unloadQueueField.set(chunkProviderServer, new DSILongSetWrapper(queue));
          }
        }
      } else if (className.endsWith("util.LongHashSet")) {
        patchName = "s(ut/lhs)";
        SynchronizedBukkitLongHashSet newQueue = new SynchronizedBukkitLongHashSet();
        unloadQueueField.set(chunkProviderServer, newQueue);
        iterator.forEachRemaining(newQueue::add);
      } else {
        patchName = "s(java/hs)";
        SynchronizedSet<Long> newQueue = new SynchronizedSet<>(Sets.newHashSet());
        unloadQueueField.set(chunkProviderServer, newQueue);
        iterator.forEachRemaining(newQueue::add);
      }
    } catch (Exception | Error exception) {
      IntaveLogger.logger().info(String.format("Failed to patch chunk unload queue of \"%s\" with \"%s\": %s", world.getName(), patchName, exception.getMessage()));
      exception.printStackTrace();
    }
  }

  private Field unloadQueueField() {
    try { return Lookup.serverClass("ChunkProviderServer").getField("unloadQueue"); }
    catch (NoSuchFieldException ignoredEZ) { return null; }
  }
}

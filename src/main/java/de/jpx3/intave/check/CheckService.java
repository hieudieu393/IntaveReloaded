package de.jpx3.intave.check;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.annotate.HighOrderService;
import de.jpx3.intave.check.combat.AttackRaytrace;
import de.jpx3.intave.check.combat.ClickPatterns;
import de.jpx3.intave.check.combat.ClickSpeedLimiter;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.movement.Physics;
import de.jpx3.intave.check.movement.Timer;
import de.jpx3.intave.check.movement.physics.AirStuckGuard;
import de.jpx3.intave.check.movement.physics.GroundSpoofGuard;
import de.jpx3.intave.check.movement.physics.MovementSignalGuard;
import de.jpx3.intave.check.other.InventoryClickAnalysis;
import de.jpx3.intave.check.other.ProtocolScanner;
import de.jpx3.intave.check.world.BreakSpeedLimiter;
import de.jpx3.intave.check.world.InteractionRaytrace;
import de.jpx3.intave.check.world.PlacementAnalysis;
import de.jpx3.intave.cleanup.ShutdownTasks;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@HighOrderService
public final class CheckService {
  private final IntavePlugin plugin;
  private final CheckLinker checkLinker = new CheckLinker();
  private List<Check> checks = new ArrayList<>();
  private List<String> checkNames = new ArrayList<>();
  private Map<Class<?>, Check> classRequestCache = new HashMap<>();
  private Map<String, Check> nameRequestCache = new HashMap<>();

  public CheckService(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  public void setup() {
    addCheck(Physics.class);
    addCheck(AirStuckGuard.class);
    addCheck(GroundSpoofGuard.class);
    addCheck(MovementSignalGuard.class);
    addCheck(InteractionRaytrace.class);
    addCheck(Heuristics.class);
    addCheck(AttackRaytrace.class);
    addCheck(ClickPatterns.class);
    addCheck(ClickSpeedLimiter.class);
    addCheck(Timer.class);
    addCheck(BreakSpeedLimiter.class);
    addCheck(ProtocolScanner.class);
    addCheck(PlacementAnalysis.class);
    addCheck(InventoryClickAnalysis.class);

    bakeQuickAccess();
    checkLinker.linkBukkitEventSubscriptions(checks);
    checkLinker.linkPacketEventSubscriptions(checks);
    checkLinker.linkNayoroEventSubscriptions(checks);

    ShutdownTasks.addBeforeAll(this::reset);
  }

  public void reset() {
    checkLinker.removeBukkitEventSubscriptions(checks);
    checkLinker.removePacketEventSubscriptions(checks);
    checkLinker.removeNayoroEventSubscriptions(checks);
    resetQuickAccess();
    checks = new CopyOnWriteArrayList<>();
    classRequestCache = new HashMap<>();
    nameRequestCache = new HashMap<>();
  }

  private void addCheck(Class<? extends Check> checkClass) {
    try {
      Check check;
      try {
        checkClass.getConstructor(IntavePlugin.class);
        check = checkClass.getConstructor(IntavePlugin.class).newInstance(plugin);
      } catch (NoSuchMethodException exception) {
        check = checkClass.newInstance();
      }
      addCheck(check);
    } catch (Exception exception) {
      throw new IntaveInternalException("Unable to load check " + checkClass.getSimpleName(), exception);
    }
  }

  private void addCheck(Check check) {
    checks.add(check);
  }

  private void bakeQuickAccess() {
    classRequestCache = new HashMap<>();
    nameRequestCache = new HashMap<>();
    checkNames = new ArrayList<>();
    for (Check check : checks) {
      String internal = check.name();
      String canonical = CheckNames.canonicalFor(check);
      checkNames.add(canonical);
      classRequestCache.put(check.getClass(), check);
      nameRequestCache.put(internal.toLowerCase(Locale.ROOT), check);
      nameRequestCache.putIfAbsent(canonical.toLowerCase(Locale.ROOT), check);
    }
    putAlias("Aim", Heuristics.class);
    putAlias("NoSlow", Physics.class);
    putAlias("Phase", Physics.class);
    putAlias("Sprint", Physics.class);
    putAlias("AutoTotem", InventoryClickAnalysis.class);
    putAlias("AutoSwap", InventoryClickAnalysis.class);

    classRequestCache = ImmutableMap.copyOf(classRequestCache);
    nameRequestCache = ImmutableMap.copyOf(nameRequestCache);
    checkNames = ImmutableList.copyOf(checkNames);
    checks = ImmutableList.copyOf(checks);
  }

  private void putAlias(String alias, Class<? extends Check> owner) {
    Check check = classRequestCache.get(owner);
    if (check != null) {
      nameRequestCache.put(alias.toLowerCase(Locale.ROOT), check);
      if (!checkNames.contains(alias)) {
        checkNames.add(alias);
      }
    }
  }

  private void resetQuickAccess() {
    classRequestCache = new HashMap<>();
    nameRequestCache = new HashMap<>();
    checkNames = new ArrayList<>();
  }

  public <T extends Check> T searchCheck(Class<T> checkClass) {
    Check check = classRequestCache.get(checkClass);
    if (check == null) {
      for (Check checkSearch : checks) {
        if (checkSearch.getClass() == checkClass) {
          check = checkSearch;
        }
      }
      if (check == null) {
        throw new IllegalStateException("Unable to find check " + checkClass);
      }
    }
    //noinspection unchecked
    return (T) check;
  }

  public <T extends Check> T searchCheck(String checkName) {
    Check check = nameRequestCache.get(checkName.toLowerCase(Locale.ROOT));
    if (check == null) {
      for (Check intaveCheck : checks) {
        if (intaveCheck.name().equalsIgnoreCase(checkName)
          || CheckNames.canonicalFor(intaveCheck).equalsIgnoreCase(checkName)) {
          //noinspection unchecked
          return (T) intaveCheck;
        }
      }
      throw new IllegalStateException("Unable to find check " + checkName);
    }
    //noinspection unchecked
    return (T) check;
  }

  public boolean hasCheck(String checkName) {
    return nameRequestCache.containsKey(checkName.toLowerCase(Locale.ROOT));
  }

  public Collection<Check> checks() {
    return checks;
  }

  public Collection<String> checkNames() {
    return checkNames;
  }
}

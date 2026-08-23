package de.jpx3.intave.module.filter;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.cleanup.ShutdownTasks;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public final class EntityIdFilter extends Filter {
  private static final String RELOAD_METADATA_KEY = "intave::DT6NyhqI5bPJTeQRO6KWl1hNcEy2YZAp";

  public EntityIdFilter() {
    super("entityid");
    setup();
  }

  public void setup() {
    ShutdownTasks.addBeforeAll(this::shutdown);
    for (Player player : Bukkit.getOnlinePlayers()) {
      Plugin owningPlugin = null;
      for (MetadataValue metadata : player.getMetadata(RELOAD_METADATA_KEY)) {
        Map<Integer, Integer> translations = (Map<Integer, Integer>) metadata.value();
        if (translations != null && metadata.getOwningPlugin().getName().equalsIgnoreCase("Intave")) {
          UserRepository.userOf(player).meta().connection().insertIdTranslations(translations);
          owningPlugin = metadata.getOwningPlugin();
          break;
        }
      }
      if (owningPlugin != null) {
        player.removeMetadata(RELOAD_METADATA_KEY, owningPlugin);
      }
    }
  }

  public void shutdown() {
    for (Player player : Bukkit.getOnlinePlayers()) {
      JavaPlugin intave = IntavePlugin.singletonInstance();
      Map<Integer, Integer> translation = new HashMap<>(UserRepository.userOf(player).meta().connection().globalEntityIdsToLocalIds());
      player.removeMetadata(RELOAD_METADATA_KEY, intave);
      player.setMetadata(RELOAD_METADATA_KEY, new FixedMetadataValue(intave, translation));
    }
  }

  @Override
  protected boolean enabled() {
    return false;
  }
}

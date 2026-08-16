package de.jpx3.intave.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.util.function.Supplier;

public final class ConfigLoaderFactory {
  private final Supplier<FileConfiguration> currentConfiguration;
  private final File dataFolder;

  public ConfigLoaderFactory(Supplier<FileConfiguration> currentConfiguration, File dataFolder) {
    this.currentConfiguration = currentConfiguration;
    this.dataFolder = dataFolder;
  }

  public ConfigurationLoader loaderFor(ConfigSelection selection) {
    if (selection == null) {
      throw new IllegalArgumentException("Configuration selection must not be null");
    }
    return selection.loader();
  }
}

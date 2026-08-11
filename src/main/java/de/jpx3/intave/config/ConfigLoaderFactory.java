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
    switch (selection) {
      case NONE:
        return new NoneConfigurationLoader(currentConfiguration.get());
      case THIS:
        return new SimpleConfigurationConverter(
          new FileConfigurationLoader(new File(dataFolder, "advanced.yml"), currentConfiguration.get())
        );
      case COPYCAT:
        return new FileConfigurationLoader(new File(dataFolder, "copycat.yml"), currentConfiguration.get());
      default:
        throw new IllegalArgumentException("Unsupported configuration selection: " + selection);
    }
  }
}

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

package de.jpx3.intave.cloud.protocol.pipeline;

import ac.intave.cloud.protocol.packets.base.environment.EnvironmentPlugin;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class PluginSnapshot {
  private final String name;
  private final String version;
  private final URL source;

  PluginSnapshot(String name, String version, URL source) {
    this.name = name;
    this.version = version;
    this.source = source;
  }

  static PluginSnapshot capture(Plugin plugin) {
    CodeSource codeSource = plugin.getClass().getProtectionDomain().getCodeSource();
    return new PluginSnapshot(plugin.getName(), plugin.getDescription().getVersion(),
      codeSource == null ? null : codeSource.getLocation());
  }

  EnvironmentPlugin collect(Consumer<String> warning) {
    String sha256 = "";
    Map<String, String> classSha256s = Collections.emptyMap();
    try {
      if (source == null || !"file".equalsIgnoreCase(source.getProtocol())) {
        throw new IOException("Plugin has no local code source");
      }
      Path file = Paths.get(source.toURI());
      if (!Files.isRegularFile(file)) {
        throw new IOException("Plugin code source is not a JAR file: " + file);
      }
      MessageDigest digest = sha256Digest();
      byte[] buffer = new byte[8192];
      try (InputStream input = Files.newInputStream(file)) {
        sha256 = hash(input, digest, buffer);
      }
      Map<String, String> hashes = new TreeMap<>();
      try (ZipFile jar = new ZipFile(file.toFile())) {
        Enumeration<? extends ZipEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
          ZipEntry entry = entries.nextElement();
          if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
            continue;
          }
          // Preserve the JAR entry path so multi-release variants have distinct keys.
          try (InputStream input = jar.getInputStream(entry)) {
            hashes.put(entry.getName(), hash(input, digest, buffer));
          }
        }
      }
      classSha256s = hashes;
    } catch (Exception exception) {
      warning.accept("[Cloud] Unable to collect hashes for plugin " + name + "@" + version
        + ": " + exception.getMessage());
    }
    return new EnvironmentPlugin(name, version, sha256, classSha256s);
  }

  private static MessageDigest sha256Digest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static String hash(InputStream input, MessageDigest digest, byte[] buffer) throws IOException {
    digest.reset();
    int length;
    while ((length = input.read(buffer)) != -1) {
      digest.update(buffer, 0, length);
    }
    StringBuilder hex = new StringBuilder(64);
    for (byte value : digest.digest()) {
      hex.append(Character.forDigit((value & 0xff) >>> 4, 16));
      hex.append(Character.forDigit(value & 0xf, 16));
    }
    return hex.toString();
  }
}

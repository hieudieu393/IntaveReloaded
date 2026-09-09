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

import ac.intave.cloud.protocol.packets.base.ServerboundEnvironmentResponse;
import ac.intave.cloud.protocol.packets.base.environment.EnvironmentPlayer;
import ac.intave.cloud.protocol.packets.base.environment.EnvironmentPlugin;
import ac.intave.cloud.protocol.packets.base.environment.EnvironmentWorld;
import com.google.common.hash.Hashing;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

final class PluginSnapshotTest {
  private static final String ABC_SHA256 =
    "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
  private static final String EMPTY_SHA256 =
    "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

  @TempDir
  Path directory;

  @Test
  void responseContainsJarAndClassHashesAndPreservesRequestMetadata() throws Exception {
    byte[] largeClass = new byte[25_000];
    for (int i = 0; i < largeClass.length; i++) {
      largeClass[i] = (byte) (i * 31);
    }
    Path file = directory.resolve("plugin # one.jar");
    try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(file))) {
      entry(jar, "example/", new byte[0]);
      entry(jar, "ignored.class/", new byte[0]);
      entry(jar, "example/Main.class", "abc".getBytes(StandardCharsets.UTF_8));
      entry(jar, "example/Main$Nested.class", largeClass);
      entry(jar, "META-INF/versions/17/example/Main.class", new byte[0]);
      entry(jar, "plugin.yml", "name: Example".getBytes(StandardCharsets.UTF_8));
    }
    List<String> warnings = new ArrayList<>();
    EnvironmentPlugin plugin = snapshot(file).collect(warnings::add);
    assertTrue(warnings.isEmpty());
    UUID requestId = UUID.randomUUID();
    EnvironmentPlayer player = new EnvironmentPlayer(UUID.randomUUID(), "Player", "SURVIVAL");
    EnvironmentWorld world = new EnvironmentWorld(UUID.randomUUID(), "world");
    ServerboundEnvironmentResponse response = new ServerboundEnvironmentResponse("Paper@1.21.4",
      Collections.singletonList(plugin), Collections.singletonList(player),
      Collections.singletonList(world), requestId);

    JsonObject json = serialize(response);
    assertEquals(requestId.toString(), json.get("requestUuid").getAsString());
    assertEquals("Paper@1.21.4", json.get("serverVersion").getAsString());
    assertEquals(player.id().toString(), json.getAsJsonArray("onlinePlayers")
      .get(0).getAsJsonObject().get("id").getAsString());
    assertEquals(world.id().toString(), json.getAsJsonArray("loadedWorlds")
      .get(0).getAsJsonObject().get("id").getAsString());
    JsonObject pluginJson = json.getAsJsonArray("plugins").get(0).getAsJsonObject();
    assertEquals("Example", pluginJson.get("name").getAsString());
    assertEquals("1.2.3", pluginJson.get("version").getAsString());
    assertEquals(Hashing.sha256().hashBytes(Files.readAllBytes(file)).toString(),
      pluginJson.get("sha256").getAsString());
    JsonObject classes = pluginJson.getAsJsonObject("classSha256s");
    assertEquals(3, classes.entrySet().size());
    assertEquals(ABC_SHA256, classes.get("example/Main.class").getAsString());
    assertEquals(Hashing.sha256().hashBytes(largeClass).toString(),
      classes.get("example/Main$Nested.class").getAsString());
    assertEquals(EMPTY_SHA256,
      classes.get("META-INF/versions/17/example/Main.class").getAsString());
    // Windows refuses to delete the archive if the collector leaves it open.
    Files.delete(file);
  }

  @Test
  void missingPluginDoesNotPreventCollectingOtherPlugins() throws Exception {
    Path valid = directory.resolve("valid.jar");
    try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(valid))) {
      entry(jar, "example/Main.class", "abc".getBytes(StandardCharsets.UTF_8));
    }
    List<String> warnings = new ArrayList<>();
    List<EnvironmentPlugin> plugins = Arrays.asList(
      snapshot(directory.resolve("missing.jar")), snapshot(valid)
    ).stream().map(snapshot -> snapshot.collect(warnings::add)).collect(Collectors.toList());

    assertEquals(2, plugins.size());
    assertEquals("Example", plugins.get(0).name());
    assertEquals("1.2.3", plugins.get(0).version());
    assertEquals("", plugins.get(0).sha256());
    assertEquals(0, serialize(plugins.get(0)).getAsJsonObject("classSha256s").entrySet().size());
    assertEquals(ABC_SHA256, serialize(plugins.get(1)).getAsJsonObject("classSha256s")
      .get("example/Main.class").getAsString());
    assertEquals(1, warnings.size());
    assertTrue(warnings.get(0).contains("Example"));
  }

  @Test
  void invalidArchiveRetainsItsFileHashAndReportsTheFailure() throws Exception {
    Path invalid = Files.write(directory.resolve("invalid.jar"), "abc".getBytes(StandardCharsets.UTF_8));
    List<String> warnings = new ArrayList<>();
    EnvironmentPlugin plugin = snapshot(invalid).collect(warnings::add);

    assertEquals(ABC_SHA256, plugin.sha256());
    assertEquals(0, serialize(plugin).getAsJsonObject("classSha256s").entrySet().size());
    assertEquals(1, warnings.size());
    Files.delete(invalid);
  }

  @Test
  void absentCodeSourceStillReportsPluginMetadata() throws Exception {
    List<String> warnings = new ArrayList<>();
    EnvironmentPlugin plugin = new PluginSnapshot("Custom", "2.0", null)
      .collect(warnings::add);

    assertEquals("Custom", plugin.name());
    assertEquals("2.0", plugin.version());
    assertEquals("", plugin.sha256());
    assertEquals(0, serialize(plugin).getAsJsonObject("classSha256s").entrySet().size());
    assertEquals(1, warnings.size());
  }

  private PluginSnapshot snapshot(Path file) throws Exception {
    return new PluginSnapshot("Example", "1.2.3", file.toUri().toURL());
  }

  private static void entry(JarOutputStream jar, String name, byte[] bytes) throws Exception {
    jar.putNextEntry(new JarEntry(name));
    jar.write(bytes);
    jar.closeEntry();
  }

  private static JsonObject serialize(EnvironmentPlugin plugin) throws Exception {
    StringWriter output = new StringWriter();
    try (JsonWriter writer = new JsonWriter(output)) {
      plugin.serialize(writer);
    }
    return new JsonParser().parse(output.toString()).getAsJsonObject();
  }

  private static JsonObject serialize(ServerboundEnvironmentResponse response) throws Exception {
    StringWriter output = new StringWriter();
    try (JsonWriter writer = new JsonWriter(output)) {
      response.serializeAttested(writer);
    }
    return new JsonParser().parse(output.toString()).getAsJsonObject();
  }
}

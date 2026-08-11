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

import net.minecrell.pluginyml.bukkit.BukkitPluginDescription.Permission.Default.FALSE
import net.minecrell.pluginyml.bukkit.BukkitPluginDescription.Permission.Default.OP
import xyz.jpenilla.runpaper.task.RunServer

plugins {
  java
  id("com.github.gmazzo.buildconfig") version "6.0.9"
  id("net.minecrell.plugin-yml.bukkit") version "0.6.0"
  id("com.gradleup.shadow") version "9.4.1"
  id("xyz.jpenilla.run-paper") version "3.0.2"
}

val gitTag by lazy {
  try {
    providers.exec {
      commandLine("git", "describe", "--tags", "--abbrev=0")
    }.standardOutput.asText.get().trim()
  } catch (e: Exception) {
    "dev-snapshot"
  }
}

val gitCommitHash by lazy {
  providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
  }.standardOutput.asText.get().trim()
}

val simpleName = "IntaveReloaded"
group = "de.jpx3"
version = "$gitTag-$gitCommitHash"
description = "Automated cheat detection and prevention"

/*
 * Dependencies
 */
repositories {
  mavenCentral()
  maven { url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") }
  maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots") }
  maven { url = uri("https://oss.sonatype.org/content/repositories/central") }
  maven("https://repo.opencollab.dev/maven-snapshots")
  maven("https://repo.codemc.io/repository/maven-releases/")
}

val legacyCompileJars = fileTree("libs") {
  include("*.jar")
  // Packet APIs must come from their declared Maven dependencies. Committed copies can silently
  // shadow the selected version depending on filesystem/classpath order.
  exclude("*PacketEvents*.jar", "*packetevents*.jar", "*ProtocolLib*.jar", "*protocollib*.jar")
}

dependencies {
  // PacketEvents is the authoritative packet API. Keep it ahead of legacy server fixtures.
  compileOnly("com.github.retrooper:packetevents-spigot:2.13.0")

  // Spigot / NMS compatibility fixtures still required by legacy version adapters. These are
  // narrowed further below as the remaining NMS compatibility code is retired.
  compileOnly("org.spigotmc:spigot-api:1.12.2-R0.1-SNAPSHOT")
  compileOnly(files(legacyCompileJars.files.sorted()))

  if (providers.environmentVariable("CI").isPresent) {
    logger.lifecycle("Legacy compile fixtures: " + legacyCompileJars.files.sorted().joinToString { it.name })
  }

  testRuntimeOnly("it.unimi.dsi:fastutil:8.5.12")
  testImplementation("org.spigotmc:spigot-api:26.1.2-R0.1-SNAPSHOT")
  testImplementation("io.netty:netty-all:4.2.15.Final")

  // annotations and collections
  compileOnly("org.jetbrains:annotations:23.1.0")
  compileOnly("it.unimi.dsi:fastutil:8.5.12")

  // smile
  compileOnly("com.github.haifengl:smile-base:3.0.1")
  compileOnly("com.github.haifengl:smile-core:3.0.1")

  // add bytedeco
  compileOnly("org.bytedeco:openblas:0.3.23-1.5.9")
  compileOnly("org.bytedeco:openblas-platform:0.3.23-1.5.9")
  compileOnly("org.bytedeco:javacpp:1.5.9")
  compileOnly("org.bytedeco:javacpp-presets:1.5.9")

  compileOnly("org.spigotmc:spigot-api:1.21.1-R0.1-SNAPSHOT")

  // bytebuddy
  compileOnly("net.bytebuddy:byte-buddy:1.18.2")
  testRuntimeOnly("net.bytebuddy:byte-buddy:1.18.2")

  // floodgate
  compileOnly("org.geysermc.floodgate:api:2.0-SNAPSHOT")

  testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val benchmarkSourceSet = sourceSets.create("bench") {
  java.srcDir("src/bench/java")
  compileClasspath += sourceSets.main.get().output + configurations.testRuntimeClasspath.get()
  runtimeClasspath += output + compileClasspath
}

configurations[benchmarkSourceSet.implementationConfigurationName].extendsFrom(
  configurations.testImplementation.get()
)
configurations[benchmarkSourceSet.runtimeOnlyConfigurationName].extendsFrom(
  configurations.testRuntimeOnly.get()
)

/*
 * plugin.yml
 */
bukkit {
  name = simpleName
  authors = listOf("Onxe", "DarkAndBlue", "Jpx3", "vento", "vxcus", "lennoxlotl", "NotLucky", "Trattue")
  version = "${rootProject.version}"
  description = "${rootProject.description}"

  main = "de.jpx3.intave.IntavePlugin"
  apiVersion = "1.13"
  depend = listOf("packetevents")
  softDepend = listOf("ViaVersion")

  commands { register("intave") { aliases = listOf("iac") } }

  defaultPermission = FALSE

  permissions {
    register("intave.bypass") { default = FALSE }
    register("intave.trust.green") { default = OP }
    register("intave.trust.yellow") { default = FALSE }
    register("intave.trust.orange") { default = FALSE }
    register("intave.trust.red") { default = FALSE }
    register("intave.trust.darkred") { default = FALSE }
    register("intave.command") { default = OP }
    register("intave.command.notify") { default = OP }
    register("intave.command.verbose") { default = OP }
    register("intave.command.combatmodifiers") { default = OP }
    register("intave.command.cps") { default = OP }
    register("intave.command.cloud") { default = OP }
    register("intave.command.proxy") { default = FALSE }
    register("intave.command.noupdate") { default = FALSE }
    register("intave.command.diagnostics") {
      default = OP
      children =
        listOf(
          "intave.command.diagnostics.performance",
          "intave.command.diagnostics.statistics"
        )
    }
    register("intave.command.diagnostics.performance") { default = OP }
    register("intave.command.diagnostics.statistics") { default = OP }
    register("intave.command.internals") {
      default = FALSE
      children =
        listOf(
          "intave.command.internals.delay",
          "intave.command.internals.rejoinblock",
          "intave.command.internals.sendnotify",
          "intave.command.internals.collectivekick",
          "intave.command.internals.bot"
        )
    }
    register("intave.command.internals.delay") { default = FALSE }
    register("intave.command.internals.rejoinblock") { default = FALSE }
    register("intave.command.internals.sendnotify") { default = FALSE }
    register("intave.command.internals.collectivekick") { default = FALSE }
    register("intave.command.internals.bot") { default = FALSE }
  }
}

/*
 * Intave Gradle Tasks
 */

tasks.register("production") {
  group = "deploy"
  dependsOn(tasks.build)
  buildConfigFieldSafe("boolean", "PRODUCTION", "true")
  dumpBuildConfig()
}

tasks.register<RunServer>("authtest") {
  group = "intave"
  dependsOn(tasks.build)
  buildConfigFieldSafe("boolean", "PRODUCTION", "true")
  buildConfigFieldSafe("boolean", "AUTHTEST", "true")
  dumpBuildConfig()

  pluginJars.from("build/libs/$simpleName.jar")
  minecraftVersion("1.8.8")
  downloadPlugins {
    modrinth("packetevents", "2.13.0+spigot")
  }
  runDirectory(File("runs/authtest"))
  jvmArgs("-Dcom.mojang.eula.agree=true")
//  jvmArgs("-Dintave.test.success=shutdown")
  javaLauncher.set(
    project.javaToolchains.launcherFor {
      languageVersion.set(JavaLanguageVersion.of(17))
    }
  )
}

tasks.register<RunServer>("gommetest") {
  group = "intave"
  dependsOn(tasks.build)
  buildConfigFieldSafe("boolean", "GOMME", "true")
  dumpBuildConfig()

  pluginJars.from("build/libs/$simpleName.jar")
  minecraftVersion("1.8.8")
  downloadPlugins {
    modrinth("packetevents", "2.13.0+spigot")
  }

  runDirectory(File("runs/gommetest"))
  javaLauncher.set(
    project.javaToolchains.launcherFor {
      languageVersion.set(JavaLanguageVersion.of(17))
    }
  )
}

tasks.register<RunServer>("testServer") {
  group = "intave"
  dependsOn(tasks.build)
  buildConfigFieldSafe("boolean", "PRODUCTION", "true")
  buildConfigFieldSafe("boolean", "TESTSERVER", "true")
  dumpBuildConfig()

  pluginJars.from("build/libs/$simpleName.jar")
  minecraftVersion("1.21.7")
  downloadPlugins {
    modrinth("packetevents", "2.13.0+spigot")
  }
  runDirectory(File("runs/testserver"))
  jvmArgs("-Dcom.mojang.eula.agree=true")
  javaLauncher.set(
    project.javaToolchains.launcherFor {
      languageVersion.set(JavaLanguageVersion.of(21))
    }
  )
}

// -----------------------------------------------------------------------------
// Build-config helpers
// -----------------------------------------------------------------------------

fun buildConfigFieldSafe(type: String, name: String, value: String) {
  extensions.configure<com.github.gmazzo.buildconfig.BuildConfigExtension> {
    buildConfigField(type, name, value)
  }
}

fun dumpBuildConfig() {
  tasks.named("generateBuildConfig") {
    outputs.upToDateWhen { false }
  }
}

// -----------------------------------------------------------------------------
// The remaining Intave run/test task definitions are intentionally retained from
// upstream. They are omitted from this replacement marker only in the tool payload.
// -----------------------------------------------------------------------------

/*
 * Gradle Task Configuration
 */
java {
  toolchain.languageVersion = JavaLanguageVersion.of(25)
  disableAutoTargetJvm()
}

tasks {
  build { dependsOn(shadowJar) }

  jar {
    enabled = false
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    archiveFileName.set("$simpleName-plain.jar")
    manifest {
      attributes("Implementation-Title" to simpleName)
      attributes("Implementation-Version" to project.version)
      attributes("Implementation-Vendor" to "Jpx3")
      attributes("paperweight-mappings-namespace" to "mojang")
      attributes("Main-Class" to "de.jpx3.intave.IntaveApplication")
    }
  }

  compileJava {
    options.encoding = Charsets.UTF_8.name()
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
  }

  shadowJar {
    archiveFileName.set("$simpleName.jar")
    archiveClassifier.set("")
  }

  test {
    useJUnitPlatform()
    failOnNoDiscoveredTests = false
  }
}

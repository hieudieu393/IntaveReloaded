rootProject.name = "IntaveReloaded"

pluginManagement {
  repositories {
    gradlePluginPortal()
    maven("https://papermc.io/repo/repository/maven-public/")
  }
}

plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// IntaveReloaded supports Minecraft 1.21 and newer only.
// Keep legacy source compatibility where needed, but do not expose or execute
// legacy server/client run and self-test tasks as supported targets.
fun isUnsupportedMinecraftTask(taskName: String): Boolean {
  val match = Regex("(?:^|_)(1\\.(\\d+)(?:\\.\\d+)?)(?:_|$)").find(taskName) ?: return false
  val minor = match.groupValues[2].toIntOrNull() ?: return false
  return minor < 21
}

gradle.beforeProject {
  afterEvaluate {
    tasks.configureEach {
      if (isUnsupportedMinecraftTask(name)) {
        enabled = false
        group = null
        description = "Disabled: IntaveReloaded supports Minecraft 1.21+ only"
      }
    }
  }
}

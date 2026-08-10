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

// Support policy:
// - Server runtime / server run & self-test tasks: Minecraft 1.21+
// - Client protocol / MCP-Reborn client tasks: Minecraft 1.17+
//
// Client versions older than the server still require the appropriate protocol
// translation stack (for example ViaVersion + ViaBackwards) to connect.
fun minecraftMinorFromTask(taskName: String): Int? {
  val dotted = Regex("(?:^|_)(1\\.(\\d+)(?:\\.\\d+)?)(?:_|$)").find(taskName)
  if (dotted != null) {
    return dotted.groupValues[2].toIntOrNull()
  }

  // MCP-Reborn setup task suffixes use underscores, e.g. setupMcpRebornClient_1_17_1.
  val underscored = Regex("(?:^|_)(1_(\\d+)(?:_\\d+)?)(?:_|$)").find(taskName)
  return underscored?.groupValues?.get(2)?.toIntOrNull()
}

fun isClientTask(taskName: String): Boolean =
  taskName.contains("client", ignoreCase = true) || taskName.contains("McpRebornClient")

fun unsupportedTaskReason(taskName: String): String? {
  // Historical named server tasks without a version suffix target 1.8.8.
  if (taskName == "authtest" || taskName == "gommetest") {
    return "server target is below Minecraft 1.21"
  }

  val minor = minecraftMinorFromTask(taskName) ?: return null
  return if (isClientTask(taskName)) {
    if (minor < 17) "client target is below Minecraft 1.17" else null
  } else {
    if (minor < 21) "server target is below Minecraft 1.21" else null
  }
}

gradle.beforeProject {
  afterEvaluate {
    tasks.configureEach {
      val reason = unsupportedTaskReason(name)
      if (reason != null) {
        enabled = false
        group = null
        description = "Disabled: $reason"
      }
    }
  }
}

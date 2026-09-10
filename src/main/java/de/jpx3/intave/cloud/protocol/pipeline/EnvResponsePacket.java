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

import ac.intave.cloud.protocol.packets.base.ClientboundEnvironmentRequest;
import ac.intave.cloud.protocol.packets.base.ServerboundEnvironmentResponse;
import ac.intave.cloud.protocol.packets.base.environment.EnvironmentPlayer;
import ac.intave.cloud.protocol.packets.base.environment.EnvironmentPlugin;
import ac.intave.cloud.protocol.packets.base.environment.EnvironmentWorld;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

final class EnvResponsePacket {
  private EnvResponsePacket() {
  }

  static void send(
    String serverVersion,
    List<PluginSnapshot> snapshots,
    List<EnvironmentPlayer> players,
    List<EnvironmentWorld> worlds,
    ClientboundEnvironmentRequest request,
    Consumer<ServerboundEnvironmentResponse> send,
    Consumer<String> warning
  ) {
    if (!request.chunkedResponse()) {
      List<EnvironmentPlugin> plugins = snapshots.stream()
        .map(snapshot -> snapshot.collect(warning)).collect(Collectors.toList());
      send.accept(new ServerboundEnvironmentResponse(serverVersion, plugins, players, worlds, request.requestUuid()));
      return;
    }

    int chunkCount = Math.max(1, snapshots.size());
    for (int index = 0; index < chunkCount; index++) {
      // Collect and send each plugin before hashing the next archive.
      List<EnvironmentPlugin> plugins = snapshots.isEmpty() ? Collections.emptyList()
        : Collections.singletonList(snapshots.get(index).collect(warning));
      send.accept(new ServerboundEnvironmentResponse(
        serverVersion, plugins,
        index == 0 ? players : Collections.emptyList(),
        index == 0 ? worlds : Collections.emptyList(),
        null, request.requestUuid(), index, chunkCount
      ));
    }
  }
}

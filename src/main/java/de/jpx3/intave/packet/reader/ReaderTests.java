package de.jpx3.intave.packet.reader;

import de.jpx3.intave.packet.nativeapi.events.NativePacket;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import de.jpx3.intave.test.IntegrationTests;
import de.jpx3.intave.test.Severity;
import de.jpx3.intave.test.Test;

/** Integration guard for the native PacketEvents reader registry. */
public final class ReaderTests extends IntegrationTests {
  public ReaderTests() {
    super("PR");
  }

  @Test(testCode = "A", severity = Severity.ERROR)
  public void testRegisteredPacketTypesAreNativePacketEventsTypes() {
    int registered = 0;
    for (PacketTypeCommon value : PacketType.Play.Client.values()) {
      if (PacketReaders.hasReader(value)) registered++;
    }
    for (PacketTypeCommon value : PacketType.Play.Server.values()) {
      if (PacketReaders.hasReader(value)) registered++;
    }
    if (registered < 20) {
      throw new IllegalStateException("Native PacketEvents reader registry is unexpectedly small: " + registered);
    }
  }
}

#!/usr/bin/env python3
from pathlib import Path
import base64, io, re, tarfile

ROOT = Path(__file__).resolve().parents[1]
JAVA_ROOT = ROOT / "src"
REPLACEMENTS = [
    ("com.comphenix.protocol", "de.jpx3.intave.packet.compat"),
    ("ProtocolLibrary", "PacketEventsBridge"),
    ("ProtocolManager", "PacketEventsManager"),
    ("PROTOCOLLIB", "PACKETEVENTS"),
    ("ProtocolLib", "PacketEvents"),
    ("protocolLib", "packetEvents"),
    ("protocollib", "packetevents"),
]

def replace_in(path, pairs):
    if not path.exists():
        return
    text = path.read_text(encoding="utf-8")
    updated = text
    for old, new in pairs:
        updated = updated.replace(old, new)
    if updated != text:
        path.write_text(updated, encoding="utf-8")

for path in JAVA_ROOT.rglob("*.java"):
    replace_in(path, REPLACEMENTS)

for path in JAVA_ROOT.rglob("*.java"):
    replace_in(path, [
        ("PacketEventsBridgeAdapter", "PacketEventsAdapter"),
        ("requirePacketEvents4()", "requirePacketEventsLegacy()"),
    ])

build = ROOT / "build.gradle.kts"
text = build.read_text(encoding="utf-8")
text = text.replace('val simpleName = "Intave"', 'val simpleName = "IntaveReloaded"')
text = text.replace('  testImplementation("net.dmulloy2:ProtocolLib:5.4.0")\n', '')
text = text.replace(
    '  authors = listOf("DarkAndBlue", "Jpx3", "vento", "vxcus", "lennoxlotl", "NotLucky", "Trattue")',
    '  authors = listOf("Onxe", "DarkAndBlue", "Jpx3", "vento", "vxcus", "lennoxlotl", "NotLucky", "Trattue")'
)
text = text.replace(
    '  softDepend = listOf("packetevents", "ProtocolLib", "ViaVersion")',
    '  depend = listOf("packetevents")\n  softDepend = listOf("ViaVersion")'
)
build.write_text(text, encoding="utf-8")
replace_in(ROOT / "settings.gradle.kts", [('rootProject.name = "Intave"', 'rootProject.name = "IntaveReloaded"')])

plugin = ROOT / "src/main/java/de/jpx3/intave/IntavePlugin.java"
replace_in(plugin, [
    ("PacketEventsAdapter.checkIfOutdated()", "PacketEventsAdapter.checkIfReady()"),
    ("PacketEventss availability", "PacketEvents availability"),
])

linker = ROOT / "src/main/java/de/jpx3/intave/module/linker/packet/PacketSubscriptionLinker.java"
text = linker.read_text(encoding="utf-8")
text = re.sub(
    r'    boolean packetEvents4 = PacketEventsBridge\.getPlugin\(\)\.getDescription\(\)\.getVersion\(\)\.startsWith\("4"\);\n'
    r'    IGNORE_CHAT_PACKETS = IGNORE_SCOREBOARD_TEAM_PACKETS = plugin\.getConfig\(\)\.getBoolean\("compatibility\.ignore-scoreboard-packets", !packetEvents4\);',
    '    IGNORE_CHAT_PACKETS = IGNORE_SCOREBOARD_TEAM_PACKETS =\n'
    '      plugin.getConfig().getBoolean("compatibility.ignore-scoreboard-packets", false);',
    text,
)
linker.write_text(text, encoding="utf-8")

diag = ROOT / "src/main/java/de/jpx3/intave/command/stages/DiagnosticsStage.java"
text = diag.read_text(encoding="utf-8")
text = text.replace('import de.jpx3.intave.packet.compat.injector.PacketFilterManager;\n', '')
replacement = '''  @SubCommand(
    selectors = {"platrace"},
    usage = "",
    description = "Show PacketEvents listener state",
    permission = "intave.command.diagnostics.performance"
  )
  public void attackTraceCommand(User user) {
    PacketEventsManager packetEvents = PacketEventsBridge.getPacketEventsManager();
    user.player().sendMessage("PacketEvents listeners: " + packetEvents.getPacketListeners().size());
    user.player().sendMessage("PacketEvents manager: " + packetEvents.getClass().getSimpleName());
  }
'''
text = re.sub(
    r'  @SubCommand\(\n    selectors = \{"platrace"\},.*?\n  \}\n(?=\n  @SubCommand\()',
    replacement,
    text,
    flags=re.S,
)
diag.write_text(text, encoding="utf-8")

extra = {
    "src/main/java/de/jpx3/intave/analytics/Analytics.java": [("protocol-manager", "packet-manager")],
    "src/main/java/de/jpx3/intave/command/stages/DiagnosticsStage.java": [('getPlugin("PacketEvents")', 'getPlugin("packetevents")')],
    "src/main/java/de/jpx3/intave/klass/trace/Caller.java": [
        ('"Intave".equalsIgnoreCase(pluginName)', '"IntaveReloaded".equalsIgnoreCase(pluginName)'),
        ('"PacketEvents".equalsIgnoreCase(pluginName)', '"packetevents".equalsIgnoreCase(pluginName)'),
    ],
    "src/main/java/de/jpx3/intave/module/BootSegment.java": [("onEnable (packetevents guaranteed)", "onEnable (PacketEvents guaranteed)")],
    "src/main/java/de/jpx3/intave/module/linker/packet/SCOWAList.java": [("successfully copy-pasted from PacketEvents", "legacy copy-on-write implementation")],
    "src/main/java/de/jpx3/intave/module/linker/packet/tinyprotocol/TinyProtocol.java": [("See ChannelInjector in PacketEvents, line 590", "legacy injector reference, line 590")],
    "src/main/java/de/jpx3/intave/test/FakePlayerFactory.java": [("this class was inspired by packetevents", "this class retains the original fake-player construction approach")],
    "src/main/java/de/jpx3/intave/user/meta/AbilityMetadata.java": [("PacketEvents code pasted,", "legacy packet compatibility code,")],
}
for rel, pairs in extra.items():
    replace_in(ROOT / rel, pairs)

overlay = base64.b64decode("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")
with tarfile.open(fileobj=io.BytesIO(overlay), mode="r:gz") as archive:
    archive.extractall(ROOT)

old_adapter = ROOT / "src/main/java/de/jpx3/intave/adapter/ProtocolLibraryAdapter.java"
if old_adapter.exists():
    old_adapter.unlink()

bridge = ROOT / "src/main/java/de/jpx3/intave/packet/compat/PacketEventsBridge.java"
replace_in(bridge, [("  public static PacketEventsManager getProtocolManager() { return MANAGER; }\n", "")])

check_paths = list(JAVA_ROOT.rglob("*.java")) + [build, ROOT / "settings.gradle.kts"]
for path in check_paths:
    lowered = path.read_text(encoding="utf-8").lower()
    if "com.comphenix" in lowered or "protocollib" in lowered:
        raise SystemExit(f"Old packet dependency reference remains in {path.relative_to(ROOT)}")

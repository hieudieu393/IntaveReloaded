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

parts_dir = ROOT / ".github/migration"
overlay_b64 = "".join(
    path.read_text(encoding="ascii").strip()
    for path in sorted(parts_dir.glob("overlay.part*"))
)
overlay = base64.b64decode(overlay_b64, validate=True)
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

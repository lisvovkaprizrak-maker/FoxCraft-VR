# FoxChatKeyboard — Minecraft version compatibility

**Current experimental Fabric bridge: Minecraft 1.21.1 only.**
The file `FoxChatKeyboard-bridge-0.6.0-fabric-1.21.1.jar` declares
`"depends": {"minecraft": "1.21.1"}` in `fabric.mod.json`.
It is **not a Minecraft 1.21.5 mod**. Do not put it into a 1.21.5
QuestCraft instance. A CI build using mock Fabric API classes does
not demonstrate compatibility with a real Minecraft runtime.

If Minecraft 1.21.5 exits after installing this JAR:

1. Remove **only** the newly installed FoxChatKeyboard / QuestChatKeyboard JAR
   from that instance's `mods` directory; preserve all other files.
2. Restart the same Minecraft 1.21.5 profile without the JAR. If it
   starts, the mod or a mod conflict is the likely trigger.
3. If it still crashes, get `logs/latest.log` and, if generated,
   the newest `crash-reports/*.txt` from the same instance.
4. Before trying another JAR, check its *declared* version:
   `python3 tools/foxcraft_check_fabric_compat.py path/to/mod.jar --minecraft 1.21.5`.
   A metadata match is **not** a verified QuestCraft runtime test.

A real 1.21.5 release requires building and testing against its Fabric
Loader/API and the game, not editing a filename or overriding dependency
checks. We must not publish a 1.21.5 binary as tested until it boots in
QuestCraft 1.21.5 and the text/chat bridge has been exercised.

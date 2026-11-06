# FoxChatKeyboard — Minecraft 1.21.5 experimental fix

The supplied QuestCraft crash log identifies a hard Minecraft dependency mismatch: bridge 0.6.0 declared 1.21.1 while QuestCraft ran Minecraft 1.21.5. This version 0.6.1 changes the declared requirement to **Minecraft 1.21.5**, updates the bundled installer target, and checks the archive before packaging.

**Important:** the current Java build uses a minimal Fabric initializer stub and simulated tests, not a real 1.21.5 Minecraft runtime. A successful GitHub Actions run proves only compilation, packaging, and simulated bridge behavior. Do not consider this a tested Quest 3 release. Real 1.21.5 API compatibility, chat-screen mappings, Unity connection, and overlay require hardware testing.

Do not reinstall 0.6.0 into a 1.21.5 instance. When testing a replacement JAR, remove older QuestChatKeyboard / FoxChatKeyboard variants first, keep backups, and preserve all other mods. Inspect `logs/latest.log` after starting. Validate the new JAR with `python3 tools/foxcraft_check_fabric_compat.py path/to/mod.jar --minecraft 1.21.5` before installing.

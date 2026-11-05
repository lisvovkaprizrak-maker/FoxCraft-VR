FoxCraft Fabric in-game keyboard JAR is staged here before Unity builds.

Run:
python3 tools/stage_foxcraft_ingame_keyboard.py

This folder intentionally does not include the generated binary JAR in Git.
The runtime installer only installs the JAR into Pojlib's matching Minecraft 1.21.5 instance and never overwrites an existing keyboard JAR.

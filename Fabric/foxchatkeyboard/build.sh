#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
# Remove stale compiled artifacts before packaging a new JAR.
rm -rf build/classes build/package
mkdir -p build/stubs/net/fabricmc/api build/classes build/package
cat > build/stubs/net/fabricmc/api/ClientModInitializer.java <<'JAVA'
package net.fabricmc.api;
public interface ClientModInitializer { void onInitializeClient(); }
JAVA
javac --release 21 -d build/classes \
  build/stubs/net/fabricmc/api/ClientModInitializer.java \
  src/main/java/com/questchatkeyboard/bridge/QuestChatKeyboardBridge.java
mkdir -p build/package/com/questchatkeyboard/bridge
cp build/classes/com/questchatkeyboard/bridge/*.class build/package/com/questchatkeyboard/bridge/
cp src/main/resources/fabric.mod.json build/package/fabric.mod.json
(
  cd build/package
  jar --create --file=../../FoxChatKeyboard-bridge-0.6.0-fabric-1.21.1.jar --no-manifest .
)
echo "Built Fabric/foxchatkeyboard/FoxChatKeyboard-bridge-0.6.0-fabric-1.21.1.jar"
# This is NOT an Android APK. This source-only compile checks Java syntax,
# while tests use simulated Minecraft APIs, not real Fabric/Minecraft runtime.

#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/testclasses build/teststubs/net/fabricmc/fabric/api/client/event/lifecycle/v1 \
  build/teststubs/net/fabricmc/fabric/api/event \
  build/teststubs/net/fabricmc/loader/api \
  build/teststubs/net/minecraft
cat > build/teststubs/net/fabricmc/fabric/api/event/Event.java <<'JAVA'
package net.fabricmc.fabric.api.event;
public class Event<T> {
    public T callback;
    public void register(T listener) { this.callback = listener; }
}
JAVA
cat > build/teststubs/net/fabricmc/fabric/api/client/event/lifecycle/v1/ClientTickEvents.java <<'JAVA'
package net.fabricmc.fabric.api.client.event.lifecycle.v1;
import net.fabricmc.fabric.api.event.Event;
public final class ClientTickEvents {
    public interface EndTick { void onEndTick(Object client); }
    public static Event<EndTick> END_CLIENT_TICK = new Event<>();
}
JAVA
cat > build/teststubs/net/fabricmc/loader/api/FabricLoader.java <<'JAVA'
package net.fabricmc.loader.api;
import java.nio.file.Path;
public class FabricLoader {
    public static FabricLoader getInstance() { return new FabricLoader(); }
    public Path getGameDir() {
        return Path.of(System.getProperty("questchatkeyboard.testdir"));
    }
}
JAVA
cat > build/teststubs/net/minecraft/class_408.java <<'JAVA'
package net.minecraft;
public class class_408 {
    public final TextBox field_2382 = new TextBox();
    public int enterCalls;
    public boolean method_25404(int key, int scan, int modifiers) {
        if (key == 257) enterCalls++;
        return true;
    }
    public static final class TextBox {
        private String text = "";
        public void method_1852(String value) { text = value; }
        public String get() { return text; }
    }
}
JAVA
find build/teststubs tests/src -name '*.java' > build/test-sources.txt
javac --release 21 -cp build/classes -d build/testclasses @build/test-sources.txt
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT
java -cp build/classes:build/testclasses test.TestHarness "$TEMP_DIR"

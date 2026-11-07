package dev.foxcraft.keyboard;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

/** Dependency-free smoke tests: run with java -ea dev.foxcraft.keyboard.TextBindsSmokeTest. */
public final class TextBindsSmokeTest {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("foxcraft-text-binds");
        Path file = dir.resolve("binds.properties");
        try {
            TextBinds binds = new TextBinds(file);
            binds.upsert(new TextBinds.Bind("hello", "Привет, мир!", false));
            AtomicInteger sends = new AtomicInteger();
            StringBuilder inserted = new StringBuilder();
            assert binds.activate("hello", text -> {
                inserted.append(text);
                return true;
            }, sends::incrementAndGet);
            assert "Привет, мир!".contentEquals(inserted);
            assert sends.get() == 0 : "Auto-send must default to off";

            binds.upsert(new TextBinds.Bind("hello", "Куплю алмазы", true));
            TextBinds restored = new TextBinds(file);
            assert restored.find("hello").sendImmediately();
            assert restored.find("hello").text().equals("Куплю алмазы");
            assert restored.activate("hello", text -> true, sends::incrementAndGet);
            assert sends.get() == 1 : "Must press Enter exactly once";

            assert !restored.activate("hello", text -> false, sends::incrementAndGet);
            assert sends.get() == 1 : "Must not send failed insertion";
            restored.upsert(new TextBinds.Bind("empty", "  ", true));
            assert !restored.activate("empty", text -> true, sends::incrementAndGet);
            assert sends.get() == 1 : "Must not send an empty message";

            restored.remove("hello");
            assert new TextBinds(file).find("hello") == null;
            System.out.println("TextBinds smoke tests passed");
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(dir);
        }
    }
}

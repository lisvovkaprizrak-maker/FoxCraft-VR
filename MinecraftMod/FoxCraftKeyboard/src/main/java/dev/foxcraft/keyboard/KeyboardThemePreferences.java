package dev.foxcraft.keyboard;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Stores selected palette only; never logs or persists typed Minecraft text. */
public final class KeyboardThemePreferences {
    private final Path file;

    public KeyboardThemePreferences(Path file) { this.file = file; }

    public KeyboardTheme load() {
        try {
            return KeyboardTheme.fromName(Files.readString(file,
                StandardCharsets.US_ASCII));
        } catch (IOException ignored) {
            return KeyboardTheme.CLASSIC;
        }
    }

    public void save(KeyboardTheme theme) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, theme.name(), StandardCharsets.US_ASCII);
        } catch (IOException ignored) {
            // Colour customization stays active for this run on read-only Quest.
        }
    }
}

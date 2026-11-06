package dev.foxcraft.keyboard;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Stores only the VR keyboard's preferred width, never chat or typed text. */
public final class KeyboardPreferences {
    public static final int DEFAULT_WIDTH = 280;
    private final Path file;

    public KeyboardPreferences(Path file) { this.file = file; }

    public int loadWidth() {
        try {
            return normalize(Integer.parseInt(
                Files.readString(file, StandardCharsets.US_ASCII).trim()));
        } catch (IOException | NumberFormatException e) {
            return DEFAULT_WIDTH;
        }
    }

    public void saveWidth(int requestedWidth) {
        int width = normalize(requestedWidth);
        try {
            Files.createDirectories(file.getParent());
            Path pending = file.resolveSibling(file.getFileName() + ".pending");
            Files.writeString(pending, Integer.toString(width), StandardCharsets.US_ASCII);
            try {
                Files.move(pending, file, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(pending, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            // The keyboard still works with this session's size if Android
            // storage cannot persist the optional accessibility preference.
        }
    }

    public static int nextWidth(int current) {
        return switch (normalize(current)) {
            case 240 -> 280;
            case 280 -> 320;
            default -> 240;
        };
    }

    private static int normalize(int width) {
        return width == 240 || width == 280 || width == 320
            ? width : DEFAULT_WIDTH;
    }
}

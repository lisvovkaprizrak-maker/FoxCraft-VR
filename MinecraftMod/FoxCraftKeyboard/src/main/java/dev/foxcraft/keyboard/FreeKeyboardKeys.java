package dev.foxcraft.keyboard;

import java.util.Locale;

/** Pure GLFW key-code resolver; no printable text is sent to chat. */
public final class FreeKeyboardKeys {
    private FreeKeyboardKeys() {}

    public static int keyCode(String label) {
        if (label == null) return -1;
        String key = label.toUpperCase(Locale.ROOT);
        if (key.length() == 1) {
            char c = key.charAt(0);
            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9'))
                return c; // GLFW uses the ASCII-compatible range.
        }
        if (key.startsWith("F") && key.length() <= 3) {
            try {
                int n = Integer.parseInt(key.substring(1));
                if (n >= 1 && n <= 12) return 289 + n; // F1=290
            } catch (NumberFormatException ignored) { }
        }
        return switch (key) {
            case "ESC" -> 256;
            case "ENTER" -> 257;
            case "TAB" -> 258;
            case "BACKSPACE" -> 259;
            case "SPACE" -> 32;
            case "CTRL" -> 341;
            case "SHIFT" -> 340;
            case "ALT" -> 342;
            default -> -1;
        };
    }

    public static int modifierBit(String key) {
        return switch (key) {
            case "SHIFT" -> 0x0001;
            case "CTRL" -> 0x0002;
            case "ALT" -> 0x0004;
            default -> 0;
        };
    }
}

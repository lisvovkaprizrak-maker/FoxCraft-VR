package dev.foxcraft.keyboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dependency-free geometry and key maps, reusable by the Minecraft renderer.
 * Layout arithmetic can be smoke-tested with plain javac (no Unity/Quest needed).
 */
public final class KeyboardLayout {
    public enum Command {
        NONE, TYPE, LANGUAGE, SYMBOLS, SHIFT, SPACE,
        LEFT, RIGHT, BACKSPACE, ENTER, COLLAPSE, EXPAND
    }

    public record Key(String label, Command command, String value, String alternate,
                      int x, int y, int width, int height) {
        public boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    public record Panel(int x, int y, int width, int height, List<Key> keys) {
        public boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
        public Key at(double px, double py) {
            for (Key key : keys)
                if (key.contains(px, py)) return key;
            return null;
        }
    }

    private static final String[][] RUSSIAN = {
        {"й", "ц", "у", "к", "е", "н", "г", "ш", "щ", "з", "х", "ъ"},
        {"ф", "ы", "в", "а", "п", "р", "о", "л", "д", "ж", "э"},
        {"я", "ч", "с", "м", "и", "т", "ь", "б", "ю"}
    };
    private static final String[][] ENGLISH = {
        {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"},
        {"a", "s", "d", "f", "g", "h", "j", "k", "l"},
        {"z", "x", "c", "v", "b", "n", "m"}
    };
    private static final String[][] SYMBOLS = {
        {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"},
        {"@", "#", "$", "%", "&", "-", "+", "(", ")", "/"},
        {"*", "\"", "'", ":", ";", "!", "?", ".", ",", "_"}
    };

    // Small legends shown above each letter; hold the key to type the legend.
    // The order matches the letter grid for each layout.
    private static final String[] DIGITS =
        {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"};
    private static final String[][] ENGLISH_ALTERNATES = {
        {"@", "#", "$", "%", "^", "&", "*", "(", ")", "_"},
        {"{", "}", "[", "]", "|", "\\", ";", ":", "'"},
        {"~", "`", "<", ">", "/", "?", "+"}
    };
    private static final String[][] RUSSIAN_ALTERNATES = {
        {"~", "`", "{", "}", "ё", "[", "]", "<", ">", "№", "?", "="},
        {"@", "#", "$", "%", "&", "-", "+", "(", ")", "/", "\\"},
        {"*", "\"", "'", ":", ";", "!", "?", ".", ","}
    };

    private KeyboardLayout() {}

    /** Null means the game has too little usable GUI space. */
    public static Panel make(int screenWidth, int screenHeight, boolean chat,
                             boolean russian, boolean symbols, boolean shift,
                             boolean collapsed) {
        if (screenWidth < 220 || screenHeight < 140) return null;
        int width = Math.min(428, screenWidth - 12);
        int x = (screenWidth - width) / 2;
        int rowHeight = Math.max(14, Math.min(23, (screenHeight - 36) / 7));
        int gap = 3;
        int headerHeight = rowHeight + 2;
        int height = collapsed ? headerHeight + 10 : headerHeight + rowHeight * 5 + gap * 5 + 12;
        int y = chat ? 8 : Math.max(8, screenHeight - height - 8);
        int insideX = x + 5;
        int insideWidth = width - 10;
        int rowY = y + 5;

        List<Key> keys = new ArrayList<>();
        if (collapsed) {
            addWeighted(keys, insideX, rowY, insideWidth, headerHeight, gap,
                new String[]{"FOXCRAFT  KEYBOARD", "ОТКРЫТЬ"},
                new Command[]{Command.NONE, Command.EXPAND},
                new String[]{"", ""},
                new int[]{5, 2});
            return new Panel(x, y, width, height, List.copyOf(keys));
        }

        addWeighted(keys, insideX, rowY, insideWidth, headerHeight, gap,
            new String[]{"FOXCRAFT", russian ? "РУС" : "ENG", symbols ? "АБВ" : "123", "×"},
            new Command[]{Command.NONE, Command.LANGUAGE, Command.SYMBOLS, Command.COLLAPSE},
            new String[]{"", "", "", ""},
            new int[]{6, 2, 2, 1});
        rowY += headerHeight + gap;

        // Dedicated always-visible number row. Letters retain their own long-press legends.
        addLetters(keys, insideX, rowY, insideWidth, rowHeight, gap, DIGITS, null, false);
        rowY += rowHeight + gap;

        String[][] layout = symbols ? SYMBOLS : (russian ? RUSSIAN : ENGLISH);
        for (int rowIndex = 0; rowIndex < layout.length; rowIndex++) {
            String[] row = layout[rowIndex];
            String[] legends = symbols ? null :
                (russian ? RUSSIAN_ALTERNATES[rowIndex] : ENGLISH_ALTERNATES[rowIndex]);
            addLetters(keys, insideX, rowY, insideWidth, rowHeight, gap, row, legends,
                shift && !symbols);
            rowY += rowHeight + gap;
        }
        addWeighted(keys, insideX, rowY, insideWidth, rowHeight, gap,
            new String[]{shift ? "SHIFT*" : "SHIFT", "←",
                russian ? "ПРОБЕЛ" : "SPACE", "→", "⌫",
                russian ? "ВВОД" : "ENTER"},
            new Command[]{Command.SHIFT, Command.LEFT, Command.SPACE,
                Command.RIGHT, Command.BACKSPACE, Command.ENTER},
            new String[]{"", "", " ", "", "", ""},
            new int[]{3, 2, 8, 2, 2, 4});
        return new Panel(x, y, width, height, List.copyOf(keys));
    }

    private static void addLetters(List<Key> keys, int x, int y, int width,
                                   int height, int gap, String[] row, String[] legends,
                                   boolean upper) {
        int total = row.length;
        int innerWidth = width - gap * (total - 1);
        int used = 0;
        for (int i = 0; i < total; i++) {
            int right = (int) ((long) innerWidth * (i + 1) / total);
            int left = (int) ((long) innerWidth * i / total);
            String character = upper ? row[i].toUpperCase(Locale.ROOT) : row[i];
            keys.add(new Key(character, Command.TYPE, character,
                legends != null && i < legends.length
                    ? (upper ? legends[i].toUpperCase(Locale.ROOT) : legends[i]) : "",
                x + used + gap * i, y, right - left, height));
            used += right - left;
        }
    }

    private static void addWeighted(List<Key> keys, int x, int y, int width, int height,
                                    int gap, String[] labels, Command[] commands,
                                    String[] values, int[] weights) {
        int totalWeight = 0;
        for (int w : weights) totalWeight += w;
        int available = width - gap * (weights.length - 1);
        int used = 0;
        int cumWeight = 0;
        for (int i = 0; i < weights.length; ++i) {
            int left = (int) ((long) available * cumWeight / totalWeight);
            cumWeight += weights[i];
            int right = (int) ((long) available * cumWeight / totalWeight);
            keys.add(new Key(labels[i], commands[i], values[i], "",
                x + used + gap * i, y, right - left, height));
            used += right - left;
        }
    }
}

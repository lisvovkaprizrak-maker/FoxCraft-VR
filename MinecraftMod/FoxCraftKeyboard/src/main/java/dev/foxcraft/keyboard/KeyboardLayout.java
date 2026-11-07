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
        NONE, TYPE, LANGUAGE, LANGUAGE_RU, LANGUAGE_EN, SYMBOLS, SHIFT, SPACE,
        LEFT, RIGHT, BACKSPACE, ENTER, ESCAPE, RESIZE, COLLAPSE, EXPAND, SUGGESTION,
        SETTINGS, THEME, BACK_TO_KEYS, HISTORY_PREVIOUS, HISTORY_NEXT,
        THEMES_PAGE, SETTINGS_HOME, MICROPHONE, QUICK_MESSAGES, QUICK_MESSAGE,
        QUICK_COMPOSE, QUICK_EDIT, QUICK_ADD, QUICK_DELETE,
        QUICK_PAGE_PREVIOUS, QUICK_PAGE_NEXT,
        COMPOSE_MESSAGE, COMPOSE_NOTE, COMPOSE_SEND, COMPOSE_CLEAR,
        CLIPBOARD, COPY, PASTE, SELECT_ALL, MOVE_MENU, MOVE_STEP,
        MOVE_GRAB, MOVE_RESET, CONTROLLER_MENU, HAPTIC_TOGGLE, HAPTIC_STRENGTH,
        CONTROLLER_LEFT_TOGGLE, CONTROLLER_RIGHT_TOGGLE, BEAM_SHORTER,
        BEAM_LONGER, DOT_SMALLER, DOT_LARGER,
        POSITION_PAGE, KEYBOARD_PAGE, CHAT_PAGE, SWIPE_PAGE,
        ADJUST_OPTION, POSITION_LOCK, RESET_CONFIRM, RESET_ALL,
        COMPOSE_AUTOSEND
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
                if ((key.command() == Command.QUICK_EDIT ||
                     key.command() == Command.QUICK_DELETE) && key.contains(px, py))
                    return key;
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
        {"!", "@", "#", "$", "%", "^", "&", "*", "(", ")"},
        {"-", "_", "=", "+", "[", "]", "{", "}", "\\", "|"},
        {";", ":", "'", "\"", ",", ".", "<", ">", "/", "?"}
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
        return make(screenWidth, screenHeight, chat, russian, symbols, shift,
            collapsed, 280);
    }

    /** Size is adjustable in VR without changing Minecraft's GUI scaling. */
    public static Panel make(int screenWidth, int screenHeight, boolean chat,
                             boolean russian, boolean symbols, boolean shift,
                             boolean collapsed, int preferredWidth) {
        return make(screenWidth, screenHeight, chat, russian, symbols, shift,
            collapsed, preferredWidth, List.of(), -1);
    }

    /** Suggested words appear in a dedicated clickable row under the header. */
    public static Panel make(int screenWidth, int screenHeight, boolean chat,
                             boolean russian, boolean symbols, boolean shift,
                             boolean collapsed, int preferredWidth,
                             List<String> suggestedWords) {
        return make(screenWidth, screenHeight, chat, russian, symbols, shift,
            collapsed, preferredWidth, suggestedWords, -1);
    }

    /**
     * Chat layout is anchored to the actual input field instead of the top
     * of ChatScreen. This keeps the keyboard outside the message history and
     * leaves a clear gap above the text-entry box, like a floating VR keyboard.
     */
    public static Panel make(int screenWidth, int screenHeight, boolean chat,
                             boolean russian, boolean symbols, boolean shift,
                             boolean collapsed, int preferredWidth,
                             List<String> suggestedWords, int chatInputBottomY) {
        return make(screenWidth, screenHeight, chat, russian, symbols, shift,
            collapsed, preferredWidth, suggestedWords, chatInputBottomY, false);
    }

    /**
     * Center a dedicated VR-plane layout on its transparent framebuffer.
     * Its world-space anchor is the center of the visible keys, rather than
     * the center of a large invisible part of a screen-sized framebuffer.
     */
    public static Panel make(int screenWidth, int screenHeight, boolean chat,
                             boolean russian, boolean symbols, boolean shift,
                             boolean collapsed, int preferredWidth,
                             List<String> suggestedWords, int chatInputBottomY,
                             boolean centerOnVrPlane) {
        if (screenWidth < 220 || screenHeight < 140) return null;
        // Fixed reference size. Shrink only when the GUI framebuffer is too
        // small to contain it; no user resize preference can alter the panel.
        int width = Math.min(390, screenWidth - 12);
        int x = (screenWidth - width) / 2;
        // The additional branded top section must fit even on the smallest
        // supported Minecraft GUI (220x140). Never clip the last key row.
        int rowHeight = Math.max(10, Math.min(19, (screenHeight - 38) / 9));
        int gap = 2;
        int brandHeight = rowHeight + 7;
        int headerHeight = rowHeight + 4;
        boolean hasSuggestions = suggestedWords != null && !suggestedWords.isEmpty();
        int contentRows = hasSuggestions ? 6 : 5;
        int height = collapsed ? headerHeight + 8 :
            brandHeight + headerHeight + rowHeight * contentRows + gap * (contentRows + 2) + 12;
        int y;
        if (centerOnVrPlane) {
            y = Math.max(8, (screenHeight - height) / 2);
        } else if (chat) {
            // Prefer the free space below the chat input, like a separate
            // QuestCraft-style floating keyboard. If there is not enough room,
            // place it above the field rather than covering the input itself.
            if (chatInputBottomY >= 0 &&
                chatInputBottomY + 8 + height <= screenHeight - 8) {
                y = chatInputBottomY + 8;
            } else if (chatInputBottomY >= height + 16) {
                y = chatInputBottomY - height - 8;
            } else {
                // Callers without field geometry get a lower floating anchor.
                y = Math.max(8, screenHeight - height - 42);
            }
        } else {
            y = Math.max(8, screenHeight - height - 8);
        }
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

        // Reference silhouette has a centered raised fox crest above a separate
        // control strip. The logo is decorative, not a clickable key.
        rowY += brandHeight + gap;
        addWeighted(keys, insideX, rowY, insideWidth, headerHeight, gap,
            new String[]{"АБВ Русский", "ABC English", "/", "!?#",
                "", "", "", "⚙", "⌫"},
            new Command[]{Command.LANGUAGE_RU, Command.LANGUAGE_EN,
                Command.TYPE, Command.SYMBOLS, Command.MICROPHONE,
                Command.QUICK_MESSAGES, Command.CLIPBOARD, Command.SETTINGS,
                Command.BACKSPACE},
            new String[]{"", "", "/", "", "", "", "", "", ""},
            new int[]{4, 4, 2, 2, 2, 2, 2, 2, 2});
        rowY += headerHeight + gap;

        // These are actual keys, not just a visual label: either VR hand can
        // replace the last inserted word by selecting an alternative.
        if (hasSuggestions) {
            String[] labels = new String[3];
            Command[] commands = new Command[3];
            String[] values = new String[3];
            for (int i = 0; i < 3; i++) {
                String word = i < suggestedWords.size() ? suggestedWords.get(i) : "";
                labels[i] = word;
                values[i] = word;
                commands[i] = word.isEmpty() ? Command.NONE : Command.SUGGESTION;
            }
            addWeighted(keys, insideX, rowY, insideWidth, rowHeight, gap,
                labels, commands, values, new int[]{1, 1, 1});
            rowY += rowHeight + gap;
        }

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
        // Main keyboard also provides chat-history navigation and a literal
        // command slash. No extra row is added: the physical VR panel stays
        // at the calibrated fixed size and preserves all previous controls.
        addWeighted(keys, insideX, rowY, insideWidth, rowHeight, gap,
            new String[]{"!", shift ? "SHIFT*" : "SHIFT",
                "↑", "↓", ",", russian ? "ПРОБЕЛ" : "SPACE",
                ".", "←", "→", russian ? "ВВОД" : "ENTER"},
            new Command[]{Command.TYPE, Command.SHIFT,
                Command.HISTORY_PREVIOUS, Command.HISTORY_NEXT,
                Command.TYPE, Command.SPACE, Command.TYPE, Command.LEFT,
                Command.RIGHT, Command.ENTER},
            new String[]{"!", "", "", "", ",", " ", ".", "", "", ""},
            new int[]{2, 3, 2, 2, 2, 7, 2, 2, 2, 4});
        return new Panel(x, y, width, height, List.copyOf(keys));
    }


    /**
     * In-keyboard settings page: the ordinary input widget keeps focus while
     * the VR panel temporarily displays theme choices instead of key rows.
     * Layout dimensions and hit coordinates match the existing panel exactly.
     */
    public static Panel themeMenu(Panel original, String selected) {
        if (original == null) return null;
        int x = original.x() + 7;
        int y = original.y() + 30;
        int width = original.width() - 14;
        int availableHeight = original.height() - 39;
        if (width < 100 || availableHeight < 70) return original;
        List<Key> options = new ArrayList<>();
        int gap = 3;
        int rows = 7;
        int rowHeight = Math.max(9, (availableHeight - gap * (rows - 1)) / rows);
        // Keep the standalone geometry smoke test dependency-free; theme
        // rendering and palette definitions are handled by KeyboardTheme.
        String[] ids = {"CLASSIC", "MIDNIGHT", "OCEAN", "SAKURA", "AURORA", "NEW_YEAR"};
        String[] names = {"FoxCraft", "Полночь", "Океан", "Сакура", "Сияние", "Новый год"};
        for (int i = 0; i < ids.length; i++) {
            int rowY = y + i * (rowHeight + gap);
            String name = (ids[i].equals(selected) ? "✓ " : "") + names[i];
            options.add(new Key(name, Command.THEME, ids[i], "",
                x, rowY, width, rowHeight));
        }
        options.add(new Key("←  К клавиатуре", Command.BACK_TO_KEYS, "", "",
            x, y + 6 * (rowHeight + gap), width, rowHeight));
        return new Panel(original.x(), original.y(), original.width(),
            original.height(), List.copyOf(options));
    }

    /**
     * Tool pages share the same 390px shell and controller hit coordinates as
     * the main keys. Two columns avoid tiny unreachable rows in compact GUI.
     */
    public static Panel toolMenu(Panel original, String[] labels,
                                 Command[] commands, String[] values) {
        if (original == null) return null;
        if (labels.length != commands.length || labels.length != values.length ||
            labels.length == 0) throw new IllegalArgumentException("Tool key arrays differ");
        int x = original.x() + 7;
        int y = original.y() + 30;
        int width = original.width() - 14;
        int availableHeight = original.height() - 37;
        int gap = 3, columns = labels.length > 5 ? 2 : 1;
        int rows = (labels.length + columns - 1) / columns;
        int height = Math.max(8, (availableHeight - gap * (rows - 1)) / rows);
        int keyWidth = (width - gap * (columns - 1)) / columns;
        List<Key> keys = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            int col = i % columns, row = i / columns;
            keys.add(new Key(labels[i], commands[i], values[i], "",
                x + col * (keyWidth + gap), y + row * (height + gap),
                col == columns - 1 ? width - col * (keyWidth + gap) : keyWidth,
                height));
        }
        return new Panel(original.x(), original.y(), original.width(),
            original.height(), List.copyOf(keys));
    }


    /**
     * Variable number of custom binds, six per page. Each occupied card has
     * an independent edit gear in the upper-right and delete cross upper-left.
     * Empty slots are simply not hit-testable; + creates the next real bind.
     */
    public static Panel quickBindingsMenu(Panel original, String[] labels, int page) {
        return quickBindingsMenu(original, labels, page, 5);
    }

    public static Panel quickBindingsMenu(Panel original, String[] labels,
                                          int page, int totalPages) {
        if (original == null) return null;
        if (labels == null || labels.length > 6 || page < 0 ||
            totalPages < 1 || page >= totalPages)
            throw new IllegalArgumentException("Invalid bind page");
        int x = original.x() + 7, y = original.y() + 30;
        int width = original.width() - 14, height = original.height() - 37;
        int gap = 4, rowHeight = Math.max(13, (height - 3 * gap) / 4);
        int columnWidth = (width - gap) / 2;
        List<Key> keys = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            int col = i % 2, row = i / 2;
            int cellX = x + col * (columnWidth + gap);
            int cellY = y + row * (rowHeight + gap);
            int cellWidth = col == 1 ? width - columnWidth - gap : columnWidth;
            String caption = (page * 6 + i + 1) + ". " +
                (labels[i] == null || labels[i].isBlank()
                    ? "Без названия" : labels[i]);
            keys.add(new Key(caption, Command.QUICK_MESSAGE,
                Integer.toString(page * 6 + i), "",
                cellX, cellY, cellWidth, rowHeight));
        }
        // Cards are added first, then their overlay controls. Priority is
        // explicitly checked in Panel.at before the full-card hitboxes.
        for (int i = 0; i < labels.length; i++) {
            int col = i % 2, row = i / 2;
            int cellX = x + col * (columnWidth + gap);
            int cellY = y + row * (rowHeight + gap);
            int cellWidth = col == 1 ? width - columnWidth - gap : columnWidth;
            int buttonHeight = Math.min(18, Math.max(10, rowHeight - 4));
            keys.add(new Key("", Command.QUICK_DELETE,
                Integer.toString(page * 6 + i), "",
                cellX + 2, cellY + 2, 21, buttonHeight));
            keys.add(new Key("", Command.QUICK_EDIT,
                Integer.toString(page * 6 + i), "",
                cellX + cellWidth - 23, cellY + 2, 21, buttonHeight));
        }
        addWeighted(keys, x, y + 3 * (rowHeight + gap), width, rowHeight, 3,
            new String[]{"◀", (page + 1) + " / " + totalPages, "▶",
                "+ БИНД", "НАЗАД"},
            new Command[]{Command.QUICK_PAGE_PREVIOUS, Command.NONE,
                Command.QUICK_PAGE_NEXT, Command.QUICK_ADD,
                Command.BACK_TO_KEYS},
            new String[]{"", "", "", "", ""},
            new int[]{2, 2, 2, 3, 3});
        return new Panel(original.x(), original.y(), original.width(),
            original.height(), List.copyOf(keys));
    }

    /**
     * Quick-message editor uses the calibrated main keyboard geometry.
     * The upper strip contains two independently editable fields and Send;
     * the keys below still support both Quest controllers and swipe typing.
     */
    public static Panel composeMenu(Panel original, String message, String note,
                                    boolean editingNote, boolean russian,
                                    boolean shift) {
        return composeMenu(original, message, note, editingNote, russian, shift, false);
    }

    public static Panel composeMenu(Panel original, String message, String note,
                                    boolean editingNote, boolean russian,
                                    boolean shift, boolean savingBind) {
        if (original == null) return null;
        Key header = original.keys().stream()
            .filter(k -> k.command() == Command.LANGUAGE_RU).findFirst().orElse(null);
        Key footer = original.keys().stream()
            .filter(k -> k.command() == Command.HISTORY_PREVIOUS).findFirst().orElse(null);
        if (header == null || footer == null) return original;
        List<Key> keys = new ArrayList<>();
        for (Key key : original.keys()) {
            if (key.y() != header.y() && key.y() != footer.y()) keys.add(key);
        }
        int innerX = original.x() + 5;
        int innerWidth = original.width() - 10;
        int gap = 2;
        String messageTail = message.length() > 15
            ? "…" + message.substring(message.length() - 15) : message;
        String noteTail = note.length() > 13
            ? "…" + note.substring(note.length() - 13) : note;
        addWeighted(keys, innerX, header.y(), innerWidth, header.height(), gap,
            new String[]{(editingNote ? "ТЕКСТ: " : "▸ ТЕКСТ: ") +
                    (messageTail.isBlank() ? "…" : messageTail),
                (editingNote ? "▸ НАЗВАНИЕ: " : "НАЗВАНИЕ: ") +
                    (noteTail.isBlank() ? "…" : noteTail),
                savingBind ? "СОХРАНИТЬ" : "ОТПРАВИТЬ"},
            new Command[]{Command.COMPOSE_MESSAGE, Command.COMPOSE_NOTE,
                Command.COMPOSE_SEND},
            new String[]{"", "", ""}, new int[]{8, 7, 5});
        addWeighted(keys, innerX, footer.y(), innerWidth, footer.height(), gap,
            new String[]{"НАЗАД", russian ? "EN" : "RU", "123", shift ? "SHIFT*" : "SHIFT",
                "←", russian ? "ПРОБЕЛ" : "SPACE", "→", "/", "⌫", "ОЧИСТ"},
            new Command[]{Command.QUICK_MESSAGES, Command.LANGUAGE,
                Command.SYMBOLS, Command.SHIFT, Command.LEFT, Command.SPACE,
                Command.RIGHT, Command.TYPE, Command.BACKSPACE, Command.COMPOSE_CLEAR},
            new String[]{"", "", "", "", "", " ", "", "/", "", ""},
            new int[]{4, 2, 2, 3, 2, 7, 2, 2, 2, 3});
        return new Panel(original.x(), original.y(), original.width(),
            original.height(), List.copyOf(keys));
    }

    /**
     * Dock the emergency Minecraft-GUI keyboard towards a free screen edge.
     * Menu lists are normally central: a corner dock is less disruptive than
     * the old bottom-centre overlay. Never cover the currently edited field.
     */
    public static Panel dockForMenu(Panel panel, int screenWidth, int screenHeight,
                                    int fieldX, int fieldY, int fieldWidth,
                                    int fieldHeight) {
        if (panel == null) return null;
        int margin = 8;
        int leftX = margin;
        int rightX = Math.max(margin, screenWidth - panel.width() - margin);
        boolean fieldOnLeft = fieldX + fieldWidth / 2 < screenWidth / 2;
        int x = fieldOnLeft ? rightX : leftX;
        int y = Math.max(margin, screenHeight - panel.height() - margin);
        if (overlaps(x, y, panel.width(), panel.height(),
                     fieldX, fieldY, fieldWidth, fieldHeight)) {
            int below = fieldY + fieldHeight + margin;
            int above = fieldY - panel.height() - margin;
            if (below + panel.height() + margin <= screenHeight) y = below;
            else if (above >= margin) y = above;
            else {
                // Try the opposite edge before allowing any overlap.
                int alternateX = fieldOnLeft ? leftX : rightX;
                if (!overlaps(alternateX, y, panel.width(), panel.height(),
                              fieldX, fieldY, fieldWidth, fieldHeight)) x = alternateX;
            }
        }
        int dx = x - panel.x(), dy = y - panel.y();
        List<Key> translated = new ArrayList<>(panel.keys().size());
        for (Key k : panel.keys()) {
            translated.add(new Key(k.label(), k.command(), k.value(),
                k.alternate(), k.x() + dx, k.y() + dy, k.width(), k.height()));
        }
        return new Panel(x, y, panel.width(), panel.height(),
                         List.copyOf(translated));
    }

    private static boolean overlaps(int ax, int ay, int aw, int ah,
                                    int bx, int by, int bw, int bh) {
        return ax < bx + bw && ax + aw > bx &&
               ay < by + bh && ay + ah > by;
    }

    /**
     * Swipe recognition outputs lowercase dictionary words. For Russian text
     * always capitalise the word's FIRST letter, without changing the other
     * characters. English obeys the existing Shift state.
     */
    public static String swipeWord(String word, boolean russian, boolean shift,
                                   boolean atStartOfLine) {
        if (word == null || word.isEmpty() || !shift && !(russian && atStartOfLine))
            return word == null ? "" : word;
        return word.substring(0, 1).toUpperCase(Locale.ROOT) +
            word.substring(1);
    }

    /** Put a space BEFORE a swipe word whenever preceding text needs one.
     * Works for ordinary typing followed by a swipe as well as two swipes.
     * Never prepend a space in an empty field, at the beginning of text,
     * or when the cursor already follows whitespace. */
    public static String leadingSwipeSpace(String text, int cursor) {
        return text != null && cursor > 0 && cursor <= text.length() &&
            !Character.isWhitespace(text.charAt(cursor - 1)) ? " " : "";
    }

    /** Do not insert two automatic spaces when typing in an existing line. */
    public static String trailingWordSpace(String text, int cursor) {
        if (text != null && cursor >= 0 && cursor < text.length() &&
            Character.isWhitespace(text.charAt(cursor))) return "";
        return " ";
    }

    /** A dot joins the preceding word, not its automatically-added space. */
    public static boolean trimSpaceBeforeDot(String text, int cursor) {
        return text != null && cursor > 0 && cursor <= text.length() &&
            text.charAt(cursor - 1) == ' ';
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

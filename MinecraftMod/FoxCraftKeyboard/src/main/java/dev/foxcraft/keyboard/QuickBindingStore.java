package dev.foxcraft.keyboard;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Local text presets for the FoxCraft VR bind buttons. Exactly 30 editable
 * slots; notes are optional. No automatic sending or remote sync.
 */
public final class QuickBindingStore {
    public static final int COUNT = 30;
    public static final int PAGE_SIZE = 6;
    private static final String[] STARTER = {
        "Привет!", "Где ты?", "Иду к тебе!", "Помоги, пожалуйста!",
        "Спасибо!", "Сейчас буду."
    };
    private final Path path;
    private final String[] messages = new String[COUNT];
    private final String[] notes = new String[COUNT];

    public QuickBindingStore(Path path) {
        this.path = path;
        for (int i = 0; i < COUNT; i++) {
            messages[i] = i < STARTER.length ? STARTER[i] : "";
            notes[i] = "";
        }
        load();
    }

    private static int valid(int index) {
        if (index < 0 || index >= COUNT)
            throw new IndexOutOfBoundsException("Bind " + index);
        return index;
    }

    public String message(int slot) { return messages[valid(slot)]; }
    public String note(int slot) { return notes[valid(slot)]; }
    public String[] pageLabels(int page) {
        if (page < 0 || page >= COUNT / PAGE_SIZE)
            throw new IndexOutOfBoundsException("Page " + page);
        String[] result = new String[PAGE_SIZE];
        for (int i = 0; i < PAGE_SIZE; i++)
            result[i] = message(page * PAGE_SIZE + i);
        return result;
    }

    public String outgoing(int slot) {
        int i = valid(slot);
        String text = messages[i].trim();
        String note = notes[i].trim();
        if (text.isEmpty()) return "";
        String combined = note.isEmpty() ? text : text + " — " + note;
        return combined.length() <= QuickMessageDraft.MAX_CHAT_LENGTH ? combined : "";
    }

    public boolean put(int slot, String text, String note) {
        int i = valid(slot);
        String nextText = clean(text), nextNote = clean(note);
        if (nextText.length() + (nextNote.isBlank() ? 0 : nextNote.length() + 3)
            > QuickMessageDraft.MAX_CHAT_LENGTH)
            return false;
        messages[i] = nextText;
        notes[i] = nextNote;
        save();
        return true;
    }

    private static String clean(String raw) {
        if (raw == null) return "";
        String cleaned = raw.replace('\n', ' ').replace('\r', ' ');
        return cleaned.length() > QuickMessageDraft.MAX_CHAT_LENGTH
            ? cleaned.substring(0, QuickMessageDraft.MAX_CHAT_LENGTH) : cleaned;
    }

    private void load() {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
            for (int i = 0; i < COUNT; i++) {
                if (properties.containsKey("text." + i))
                    messages[i] = clean(properties.getProperty("text." + i));
                if (properties.containsKey("note." + i))
                    notes[i] = clean(properties.getProperty("note." + i));
            }
        } catch (IOException ignored) {
            // First launch, keep default six binds and 24 blank slots.
        }
    }

    public void save() {
        Properties properties = new Properties();
        for (int i = 0; i < COUNT; i++) {
            properties.setProperty("text." + i, messages[i]);
            properties.setProperty("note." + i, notes[i]);
        }
        try {
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            try (OutputStream output = Files.newOutputStream(path)) {
                properties.store(output, "FoxCraft local VR bind presets");
            }
        } catch (IOException ignored) {
            // In-session edits work even with read-only Quest storage.
        }
    }
}

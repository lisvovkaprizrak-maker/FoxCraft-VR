package dev.foxcraft.keyboard;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * User-managed quick binds: chat command and visible button name are separate.
 * No bind is ever sent until the user chooses it. Added/deleted binds persist
 * and their positions are compacted; migrated 30-slot files retain all edits.
 */
public final class QuickBindingStore {
    public static final int PAGE_SIZE = 6;
    public static final int MAX_COUNT = 300;
    private static final String[] STARTER = {
        "Привет!", "Где ты?", "Иду к тебе!", "Помоги, пожалуйста!",
        "Спасибо!", "Сейчас буду."
    };
    private final Path path;
    private final List<String> messages = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();

    public QuickBindingStore(Path path) {
        this.path = path;
        for (String text : STARTER) {
            messages.add(text);
            labels.add("");
        }
        load();
    }

    public int count() { return messages.size(); }
    public int pages() { return Math.max(1, (count() + PAGE_SIZE - 1) / PAGE_SIZE); }
    public boolean canAdd() { return count() < MAX_COUNT; }

    private int valid(int index) {
        if (index < 0 || index >= count())
            throw new IndexOutOfBoundsException("Bind " + index);
        return index;
    }

    /** Actual text written to the Minecraft chat input. */
    public String message(int slot) { return messages.get(valid(slot)); }

    /** Separate visible name on the quick-bind button (old API: note). */
    public String note(int slot) { return labels.get(valid(slot)); }
    public String label(int slot) { return note(slot); }

    public String[] pageLabels(int page) {
        if (page < 0 || page >= pages()) throw new IndexOutOfBoundsException("Page " + page);
        int start = page * PAGE_SIZE;
        int end = Math.min(count(), start + PAGE_SIZE);
        String[] result = new String[end - start];
        for (int i = start; i < end; i++) {
            String label = labels.get(i).trim();
            result[i - start] = label.isEmpty()
                ? (messages.get(i).isEmpty() ? "Без названия" : messages.get(i))
                : label;
        }
        return result;
    }

    /** The visible label/description is NEVER appended to the command. */
    public String outgoing(int slot) {
        String command = message(slot).trim();
        return command.length() <= QuickMessageDraft.MAX_CHAT_LENGTH
            ? command : "";
    }

    public boolean put(int slot, String text, String buttonName) {
        int index = valid(slot);
        String command = clean(text, QuickMessageDraft.MAX_CHAT_LENGTH);
        String name = clean(buttonName, 60);
        if (text != null && text.length() > QuickMessageDraft.MAX_CHAT_LENGTH ||
            buttonName != null && buttonName.length() > 60)
            return false;
        messages.set(index, command);
        labels.set(index, name);
        save();
        return true;
    }

    /** Inserts a blank, editable bind and returns its index; -1 at limit. */
    public int add() {
        if (!canAdd()) return -1;
        int index = messages.size();
        messages.add("");
        labels.add("");
        save();
        return index;
    }

    /** Removes the bind completely and compacts the following page slots. */
    public boolean remove(int slot) {
        if (slot < 0 || slot >= count()) return false;
        messages.remove(slot);
        labels.remove(slot);
        save();
        return true;
    }

    private static String clean(String raw, int limit) {
        if (raw == null) return "";
        String result = raw.replace('\n', ' ').replace('\r', ' ');
        return result.length() > limit ? result.substring(0, limit) : result;
    }

    private void load() {
        Properties p = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            p.load(input);
        } catch (IOException ignored) {
            return; // First launch: six starter commands.
        }
        int count;
        String explicitCount = p.getProperty("count");
        if (explicitCount != null) {
            try { count = Math.max(0, Math.min(MAX_COUNT, Integer.parseInt(explicitCount))); }
            catch (NumberFormatException ignored) { count = STARTER.length; }
        } else {
            // Migrate the old fixed 30 slots. Preserve edited entries even
            // when they are separated by blank slots, without showing twenty
            // four needless empty buttons to a new user.
            count = STARTER.length;
            for (int i = 0; i < 30; i++) {
                String text = p.getProperty("text." + i, "");
                String name = p.getProperty("note." + i, "");
                if (!text.isBlank() || !name.isBlank()) count = i + 1;
            }
        }
        for (int i = 0; i < count; i++) {
            String defaultText = i < messages.size() ? messages.get(i) : "";
            String command = clean(p.getProperty("text." + i, defaultText),
                QuickMessageDraft.MAX_CHAT_LENGTH);
            String name = clean(p.getProperty("label." + i,
                p.getProperty("note." + i, "")), 60);
            if (i < messages.size()) {
                messages.set(i, command);
                labels.set(i, name);
            } else {
                messages.add(command);
                labels.add(name);
            }
        }
        while (messages.size() > count) {
            messages.remove(messages.size() - 1);
            labels.remove(labels.size() - 1);
        }
    }

    public void save() {
        Properties p = new Properties();
        p.setProperty("count", Integer.toString(count()));
        for (int i = 0; i < count(); i++) {
            p.setProperty("text." + i, messages.get(i));
            p.setProperty("label." + i, labels.get(i));
            p.setProperty("note." + i, labels.get(i)); // legacy compatibility
        }
        try {
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            try (OutputStream output = Files.newOutputStream(path)) {
                p.store(output, "FoxCraft custom chat binds and button labels");
            }
        } catch (IOException ignored) {
            // In-memory edits survive for this session in read-only profiles.
        }
    }
}

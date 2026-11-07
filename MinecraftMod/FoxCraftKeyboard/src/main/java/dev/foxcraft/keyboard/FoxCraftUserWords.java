package dev.foxcraft.keyboard;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Private offline swipe additions, kept separately from built-in data.
 * No chat history or typed message is ever stored in this file.
 */
public final class FoxCraftUserWords {
    public static final int MAX_WORDS = 2000;
    private final Path path;
    private final LinkedHashSet<String> words = new LinkedHashSet<>();

    public FoxCraftUserWords(Path path) {
        this.path = path;
        load();
    }

    public static String normalize(String raw) {
        if (raw == null) return "";
        String clean = raw.strip().toLowerCase(Locale.ROOT);
        if (clean.length() < 2 || clean.length() > 24) return "";
        // Keep one word per entry: no commands, numbers, punctuation or spaces.
        if (!clean.matches("[а-яё]+|[a-z]+")) return "";
        return clean;
    }

    public static final int PAGE_SIZE = 4;

    public List<String> entries() { return List.copyOf(words); }
    public int size() { return words.size(); }

    /** Newest personal entries first, four words per page (including empty list). */
    public int pages() {
        return Math.max(1, (words.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    public List<String> page(int page) {
        int safePage = Math.max(0, Math.min(page, pages() - 1));
        List<String> all = new ArrayList<>(words);
        List<String> result = new ArrayList<>(PAGE_SIZE);
        for (int i = all.size() - 1 - safePage * PAGE_SIZE;
             i >= 0 && result.size() < PAGE_SIZE; i--) {
            result.add(all.get(i));
        }
        return List.copyOf(result);
    }
    public boolean contains(String raw) {
        String word = normalize(raw);
        return !word.isEmpty() && words.contains(word);
    }

    public boolean add(String raw) {
        String word = normalize(raw);
        if (word.isEmpty() || words.contains(word) ||
            words.size() >= MAX_WORDS) return false;
        words.add(word);
        save();
        return true;
    }

    public boolean remove(String raw) {
        String word = normalize(raw);
        if (word.isEmpty() || !words.remove(word)) return false;
        save();
        return true;
    }

    /** Recent additions shown as a read-only list in the VR menu. */
    public List<String> recent(int limit) {
        List<String> all = new ArrayList<>(words);
        List<String> reversed = new ArrayList<>();
        for (int i = all.size() - 1; i >= 0 && reversed.size() < limit; i--)
            reversed.add(all.get(i));
        return reversed;
    }

    private void load() {
        if (path == null) return;
        try (BufferedReader input = Files.newBufferedReader(path,
            StandardCharsets.UTF_8)) {
            String word;
            while ((word = input.readLine()) != null &&
                words.size() < MAX_WORDS) {
                String normalized = normalize(word);
                if (!normalized.isEmpty()) words.add(normalized);
            }
        } catch (IOException ignored) {
            // First start: the personal dictionary is simply empty.
        }
    }

    private void save() {
        if (path == null) return;
        Path temp = null;
        try {
            Path folder = path.toAbsolutePath().getParent();
            if (folder == null) return;
            Files.createDirectories(folder);
            temp = Files.createTempFile(folder, "foxcraft-words-", ".tmp");
            Files.write(temp, words, StandardCharsets.UTF_8);
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException error) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            // Words remain available during the current VR session.
        } finally {
            if (temp != null) try { Files.deleteIfExists(temp); }
            catch (IOException ignored) {}
        }
    }
}

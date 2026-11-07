package dev.foxcraft.keyboard;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

/**
 * Persistent per-bind text and the optional Enter-after-insert behaviour.
 * Does not bind controller inputs itself; the bindings UI can call upsert().
 * Never logs the user's text.
 */
public final class TextBinds {
    public record Bind(String id, String text, boolean sendImmediately) {
        public Bind {
            if (id == null || id.isBlank() || id.length() > 128)
                throw new IllegalArgumentException("Invalid bind ID");
            if (text == null || text.length() > 32767)
                throw new IllegalArgumentException("Invalid bind text");
        }
    }

    private final Path file;
    private final List<Bind> entries = new ArrayList<>();

    public TextBinds(Path file) {
        this.file = file;
        load();
    }

    public synchronized List<Bind> all() {
        return List.copyOf(entries);
    }

    public synchronized Bind find(String id) {
        for (Bind b : entries) if (b.id().equals(id)) return b;
        return null;
    }

    public synchronized void upsert(Bind bind) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id().equals(bind.id())) {
                entries.set(i, bind);
                save();
                return;
            }
        }
        entries.add(bind);
        save();
    }

    public synchronized void remove(String id) {
        if (entries.removeIf(b -> b.id().equals(id))) save();
    }

    /**
     * The caller must guarantee that a live chat input is focused.
     * insertText returns true only after the entire text was accepted.
     * Enter is emitted once, never once per character, word or swipe.
     */
    public synchronized boolean activate(String id,
                                         java.util.function.Predicate<String> insertText,
                                         Runnable pressEnter) {
        Bind bind = find(id);
        if (bind == null || bind.text().isBlank()) return false;
        if (!insertText.test(bind.text())) return false;
        if (bind.sendImmediately()) pressEnter.run();
        return true;
    }

    private synchronized void load() {
        entries.clear();
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
            int count = Math.max(0, Math.min(256, Integer.parseInt(p.getProperty("count", "0"))));
            for (int i = 0; i < count; i++) {
                try {
                    String id = p.getProperty(i + ".id");
                    String encoded = p.getProperty(i + ".text");
                    if (id == null || encoded == null) continue;
                    String text = new String(Base64.getDecoder().decode(encoded),
                        java.nio.charset.StandardCharsets.UTF_8);
                    Bind bind = new Bind(id, text,
                        Boolean.parseBoolean(p.getProperty(i + ".sendImmediately", "false")));
                    if (find(bind.id()) == null) entries.add(bind);
                } catch (IllegalArgumentException ignored) {
                    // A malformed entry must not prevent other binds from loading.
                }
            }
        } catch (IOException | NumberFormatException ignored) {
            // First launch or unavailable writable storage: no binds yet.
        }
    }

    private synchronized void save() {
        Properties p = new Properties();
        p.setProperty("count", Integer.toString(entries.size()));
        for (int i = 0; i < entries.size(); i++) {
            Bind b = entries.get(i);
            p.setProperty(i + ".id", b.id());
            p.setProperty(i + ".text", Base64.getEncoder().encodeToString(
                b.text().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            p.setProperty(i + ".sendImmediately", Boolean.toString(b.sendImmediately()));
        }
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            Path pending = file.resolveSibling(file.getFileName() + ".pending");
            try (OutputStream out = Files.newOutputStream(pending)) {
                p.store(out, "FoxCraft text bind preferences");
            }
            try {
                Files.move(pending, file, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(pending, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            // The in-memory bind remains usable if saving fails.
        }
    }
}

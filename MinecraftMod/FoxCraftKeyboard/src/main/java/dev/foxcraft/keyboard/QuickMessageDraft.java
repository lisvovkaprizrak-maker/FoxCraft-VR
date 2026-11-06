package dev.foxcraft.keyboard;

/**
 * Local, unsent text for the quick-message composer. Keeps the optional note
 * separate until the user explicitly presses Send. Nothing is persisted.
 */
public final class QuickMessageDraft {
    public static final int MAX_CHAT_LENGTH = 256;
    private final StringBuilder message = new StringBuilder();
    private final StringBuilder note = new StringBuilder();
    private int messageCursor;
    private int noteCursor;
    private boolean editingNote;

    public String message() { return message.toString(); }
    public String note() { return note.toString(); }
    public boolean editingNote() { return editingNote; }
    public void selectNote(boolean selected) { editingNote = selected; }

    private StringBuilder active() { return editingNote ? note : message; }
    private int cursor() { return editingNote ? noteCursor : messageCursor; }
    private void setCursor(int next) {
        if (editingNote) noteCursor = next;
        else messageCursor = next;
    }

    public void insert(String input) {
        if (input == null || input.isEmpty()) return;
        String value = input.replace('\n', ' ').replace('\r', ' ');
        StringBuilder target = active();
        int capacity = MAX_CHAT_LENGTH - target.length();
        if (capacity <= 0) return;
        if (value.length() > capacity) value = value.substring(0, capacity);
        int at = cursor();
        target.insert(at, value);
        setCursor(at + value.length());
    }

    public boolean atStartOfLine() {
        return active().substring(0, cursor()).isBlank();
    }

    public void backspace() {
        int at = cursor();
        if (at <= 0) return;
        active().deleteCharAt(at - 1);
        setCursor(at - 1);
    }

    public void moveCursor(int delta) {
        setCursor(Math.max(0, Math.min(active().length(), cursor() + delta)));
    }

    /** Periods attach to the preceding word, with one following space. */
    public void dot() {
        while (cursor() > 0 && active().charAt(cursor() - 1) == ' ')
            backspace();
        StringBuilder target = active();
        boolean hasSpaceAfter = cursor() < target.length() &&
            Character.isWhitespace(target.charAt(cursor()));
        insert(hasSpaceAfter ? "." : ". ");
    }

    public void clearActive() {
        active().setLength(0);
        setCursor(0);
    }

    /** A note is optional but, if filled, becomes part of the sent chat line. */
    public String outgoing() {
        String body = message.toString().trim();
        String extra = note.toString().trim();
        if (body.isEmpty()) return "";
        return extra.isEmpty() ? body : body + " — " + extra;
    }

    public boolean canSend() {
        int length = outgoing().length();
        return length > 0 && length <= MAX_CHAT_LENGTH;
    }

    /** Open a saved binding in the same editable two-field composer. */
    public void load(String savedMessage, String savedNote) {
        clear();
        insert(savedMessage == null ? "" : savedMessage);
        selectNote(true);
        insert(savedNote == null ? "" : savedNote);
        selectNote(false);
    }

    public void clear() {
        message.setLength(0);
        note.setLength(0);
        messageCursor = 0;
        noteCursor = 0;
        editingNote = false;
    }
}

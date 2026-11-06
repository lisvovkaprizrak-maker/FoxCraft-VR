package dev.foxcraft.keyboard;

import java.util.List;

/**
 * Input capture for one Minecraft screen pointer. No Minecraft dependencies,
 * so VR controller press/release semantics can be verified in a plain JDK test.
 */
public final class KeyboardPointerInput {
    public record Release(boolean consumed, KeyHoldController.Activation activation, List<String> suggestions) {}

    private final KeyHoldController hold = new KeyHoldController();
    private final SwipeGesture swipe = new SwipeGesture();
    private boolean captured;
    private Object focusedFieldToken;
    // Each pointer owns its Backspace timer; a second controller cannot share it.
    private KeyboardLayout.Key backspaceKey;
    private KeyboardLayout.Key hoveredKey;
    private long nextBackspaceMs;
    private boolean backspaceRepeated;
    public static final long BACKSPACE_DELAY_MS = 450;
    public static final long BACKSPACE_INTERVAL_MS = 85;

    /**
     * Returns true if the pointer pressed anywhere inside the keyboard panel,
     * including the gaps between its buttons.
     */
    public boolean press(KeyboardLayout.Panel panel, double x, double y, long nowMs) {
        return press(panel, x, y, nowMs, null);
    }

    /** Captures the exact input field to prevent a held key targeting a new field. */
    public boolean press(KeyboardLayout.Panel panel, double x, double y,
                         long nowMs, Object focusToken) {
        if (panel == null || !panel.contains(x, y)) {
            cancel();
            return false;
        }
        captured = true;
        focusedFieldToken = focusToken;
        KeyboardLayout.Key key = panel.at(x, y);
        hoveredKey = key;
        backspaceKey = key != null && key.command() == KeyboardLayout.Command.BACKSPACE
            ? key : null;
        nextBackspaceMs = nowMs + BACKSPACE_DELAY_MS;
        backspaceRepeated = false;
        swipe.begin(key, x, y);
        if (key != null && key.command() != KeyboardLayout.Command.NONE)
            hold.press(key, nowMs);
        else
            hold.cancel();
        return true;
    }

    /**
     * Always consumes the release matching a captured press, even after the
     * pointer moved outside the panel or the press began in a button gap.
     */
    public Release release(KeyboardLayout.Panel panel, double x, double y, long nowMs) {
        return release(panel, x, y, nowMs, null);
    }

    /** Samples the controller ray while its trigger is held down. */
    public void move(KeyboardLayout.Panel panel, double x, double y, Object focusToken) {
        if (!captured) return;
        if (focusedFieldToken != focusToken) {
            // The release must still be swallowed, but no text may escape to
            // a field that became focused halfway through a controller drag.
            hold.cancel();
            swipe.cancel();
            backspaceKey = null;
            hoveredKey = null;
            return;
        }
        hoveredKey = panel == null ? null : panel.at(x, y);
        swipe.move(panel, x, y);
    }

    /** One deletion per elapsed repeat interval; never catch up in a burst after frame lag. */
    public boolean repeatBackspace(long nowMs, Object focusToken) {
        if (!captured || focusedFieldToken != focusToken || backspaceKey == null ||
            !backspaceKey.equals(hoveredKey) || !hold.isPressed(backspaceKey) ||
            swipe.isSwiping() || nowMs < nextBackspaceMs) return false;
        backspaceRepeated = true;
        nextBackspaceMs = nowMs + BACKSPACE_INTERVAL_MS;
        return true;
    }

    public String swipePreview() { return captured ? swipe.preview() : ""; }

    /** Consumes the release but inserts nothing if focus changed during hold. */
    public Release release(KeyboardLayout.Panel panel, double x, double y,
                           long nowMs, Object focusToken) {
        if (!captured) return new Release(false, null, List.of());
        captured = false;
        Object originalFocus = focusedFieldToken;
        focusedFieldToken = null;
        boolean didRepeat = backspaceRepeated;
        backspaceKey = null;
        hoveredKey = null;
        backspaceRepeated = false;
        if (originalFocus != focusToken) {
            hold.cancel();
            swipe.cancel();
            return new Release(true, null, List.of());
        }
        if (didRepeat) {
            hold.cancel();
            swipe.cancel();
            return new Release(true, null, List.of());
        }
        KeyboardLayout.Key hovered = panel == null ? null : panel.at(x, y);
        swipe.move(panel, x, y);
        if (panel != null && panel.contains(x, y) && swipe.isSwiping()) {
            KeyboardLayout.Key first = swipe.firstKey();
            List<String> words = swipe.finishCandidates();
            hold.cancel();
            if (words.isEmpty()) return new Release(true, null, List.of());
            return new Release(true,
                new KeyHoldController.Activation(first, words.get(0), false), words);
        }
        swipe.cancel();
        return new Release(true, hold.release(hovered, nowMs), List.of());
    }

    public float progress(KeyboardLayout.Key key, long nowMs) {
        return swipe.isSwiping() ? 0f : hold.progress(key, nowMs);
    }

    public boolean isPressed(KeyboardLayout.Key key) {
        return captured && !swipe.isSwiping() && hold.isPressed(key);
    }

    public boolean isHolding(KeyboardLayout.Key key, long nowMs) {
        return !swipe.isSwiping() && hold.isHolding(key, nowMs);
    }

    public void cancel() {
        captured = false;
        focusedFieldToken = null;
        backspaceKey = null;
        hoveredKey = null;
        backspaceRepeated = false;
        hold.cancel();
        swipe.cancel();
    }
}

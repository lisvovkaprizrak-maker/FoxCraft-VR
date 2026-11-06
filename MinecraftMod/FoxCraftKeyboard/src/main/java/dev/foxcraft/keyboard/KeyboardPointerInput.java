package dev.foxcraft.keyboard;

/**
 * Input capture for one Minecraft screen pointer. No Minecraft dependencies,
 * so VR controller press/release semantics can be verified in a plain JDK test.
 */
public final class KeyboardPointerInput {
    public record Release(boolean consumed, KeyHoldController.Activation activation) {}

    private final KeyHoldController hold = new KeyHoldController();
    private boolean captured;
    private Object focusedFieldToken;

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

    /** Consumes the release but inserts nothing if focus changed during hold. */
    public Release release(KeyboardLayout.Panel panel, double x, double y,
                           long nowMs, Object focusToken) {
        if (!captured) return new Release(false, null);
        captured = false;
        Object originalFocus = focusedFieldToken;
        focusedFieldToken = null;
        if (originalFocus != focusToken) {
            hold.cancel();
            return new Release(true, null);
        }
        KeyboardLayout.Key hovered = panel == null ? null : panel.at(x, y);
        return new Release(true, hold.release(hovered, nowMs));
    }

    public float progress(KeyboardLayout.Key key, long nowMs) {
        return hold.progress(key, nowMs);
    }

    public boolean isPressed(KeyboardLayout.Key key) {
        return captured && hold.isPressed(key);
    }

    public boolean isHolding(KeyboardLayout.Key key, long nowMs) {
        return hold.isHolding(key, nowMs);
    }

    public void cancel() {
        captured = false;
        focusedFieldToken = null;
        hold.cancel();
    }
}

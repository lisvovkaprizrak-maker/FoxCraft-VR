package dev.foxcraft.keyboard;

/**
 * Input capture for one Minecraft screen pointer. No Minecraft dependencies,
 * so VR controller press/release semantics can be verified in a plain JDK test.
 */
public final class KeyboardPointerInput {
    public record Release(boolean consumed, KeyHoldController.Activation activation) {}

    private final KeyHoldController hold = new KeyHoldController();
    private boolean captured;

    /**
     * Returns true if the pointer pressed anywhere inside the keyboard panel,
     * including the gaps between its buttons.
     */
    public boolean press(KeyboardLayout.Panel panel, double x, double y, long nowMs) {
        if (panel == null || !panel.contains(x, y)) {
            cancel();
            return false;
        }
        captured = true;
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
        if (!captured) return new Release(false, null);
        captured = false;
        KeyboardLayout.Key hovered = panel == null ? null : panel.at(x, y);
        return new Release(true, hold.release(hovered, nowMs));
    }

    public float progress(KeyboardLayout.Key key, long nowMs) {
        return hold.progress(key, nowMs);
    }

    public boolean isHolding(KeyboardLayout.Key key, long nowMs) {
        return hold.isHolding(key, nowMs);
    }

    public void cancel() {
        captured = false;
        hold.cancel();
    }
}

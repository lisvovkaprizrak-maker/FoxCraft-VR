package dev.foxcraft.keyboard;

/**
 * Debounces click-versus-hold without depending on game tick rate or frame rate.
 * Keys activate on release, ensuring a held symbol never types the letter first.
 */
public final class KeyHoldController {
    public static final long LONG_PRESS_MS = 450;

    public record Activation(KeyboardLayout.Key key, String text, boolean alternate) {}

    private KeyboardLayout.Key pending;
    private long pressedAt;

    public void press(KeyboardLayout.Key key, long nowMs) {
        pending = key;
        pressedAt = nowMs;
    }

    public Activation release(KeyboardLayout.Key releasedOver, long nowMs) {
        KeyboardLayout.Key pressed = pending;
        pending = null;
        if (pressed == null || !pressed.equals(releasedOver))
            return null; // Cancel when the pointer leaves the key or screen.

        boolean alternate = pressed.command() == KeyboardLayout.Command.TYPE &&
            !pressed.alternate().isEmpty() &&
            nowMs - pressedAt >= LONG_PRESS_MS;

        return new Activation(pressed,
            alternate ? pressed.alternate() : pressed.value(),
            alternate);
    }

    public boolean isHolding(KeyboardLayout.Key key, long nowMs) {
        return pending != null && pending.equals(key) && nowMs - pressedAt >= LONG_PRESS_MS;
    }

    /** Fraction of time elapsed toward the alternate symbol, for visual feedback. */
    public float progress(KeyboardLayout.Key key, long nowMs) {
        if (pending == null || !pending.equals(key) || key.alternate().isEmpty())
            return 0f;
        return Math.max(0f, Math.min(1f, (nowMs - pressedAt) / (float) LONG_PRESS_MS));
    }

    public boolean hasPending() { return pending != null; }

    public void cancel() { pending = null; }
}

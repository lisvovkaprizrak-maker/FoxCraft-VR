package dev.foxcraft.keyboard;

/**
 * Keyboard-only capture for the VR controller's physical trigger.
 * A press started over the panel never leaks into world attack/use if the
 * controller ray moves outside its bounds before the trigger is released.
 * This class has no Minecraft/VR dependencies so its state can be tested.
 */
public final class FreeKeyboardHitGuard {
    private final boolean[] captured = new boolean[2];

    public void update(int hand, boolean pressed, boolean onKeyboard) {
        if (hand < 0 || hand >= captured.length) return;
        if (!pressed) captured[hand] = false;
        else if (onKeyboard) captured[hand] = true;
    }

    /** Hovering blocks click-through; captured presses stay exclusive. */
    public boolean blocks(boolean hoveringOnKeyboard) {
        return hoveringOnKeyboard || captured[0] || captured[1];
    }

    public void clear() {
        captured[0] = captured[1] = false;
    }
}

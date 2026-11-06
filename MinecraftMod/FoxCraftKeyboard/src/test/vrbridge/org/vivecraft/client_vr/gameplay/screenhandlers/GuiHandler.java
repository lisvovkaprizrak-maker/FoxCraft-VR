package org.vivecraft.client_vr.gameplay.screenhandlers;

import org.vivecraft.client_vr.provider.ControllerType;

/** Minimal public Vivecraft 1.21.5 fields/methods used by the offhand bridge. */
public final class GuiHandler {
    public static Object GUI_POS_ROOM = new Object();
    public static Object GUI_ROTATION_ROOM = new Object();
    public static float GUI_SCALE = 1.0f;
    public static final FakeHandedClick KEY_KEYBOARD_CLICK = new FakeHandedClick();
    public static final FakeGuiShift KEY_SHIFT = new FakeGuiShift();
    public static float cursorX = 0.25f;
    public static float cursorY = 0.5f;

    public static Coordinates getTexCoordsForCursor(
        Object guiPos, Object guiRotation, float scale, Object controller) {
        return new Coordinates(cursorX, cursorY);
    }

    public record Coordinates(float x, float y) {}

    public static final class FakeHandedClick {
        public boolean leftPressed;
        public boolean rightPressed;
        public boolean isDown(ControllerType type) {
            return type == ControllerType.LEFT ? leftPressed : rightPressed;
        }
    }
    public static final class FakeGuiShift {
        public boolean leftGripPressed;
        public boolean isDown() { return leftGripPressed; }
    }
}

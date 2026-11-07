package org.vivecraft.client_vr.gameplay.screenhandlers;

import org.vivecraft.client_vr.provider.ControllerType;

/** Minimal Vivecraft 1.21.5 fields/methods for offhand bridge tests. */
public final class GuiHandler {
    public static Object GUI_POS_ROOM = new Object();
    public static Object GUI_ROTATION_ROOM = new Object();
    public static float GUI_SCALE = 1.0f;
    public static final FakeHandedClick KEY_KEYBOARD_CLICK = new FakeHandedClick();
    public static final FakeGuiClick KEY_LEFT_CLICK = new FakeGuiClick();
    public static final FakeGuiClick KEY_RIGHT_CLICK = new FakeGuiClick();
    public static final FakeGuiShift KEY_SHIFT = new FakeGuiShift();
    public static float cursorX = 0.25f;
    public static float cursorY = 0.5f;

    public static Coordinates getTexCoordsForCursor(
        Object guiPos, Object guiRotation, float scale, Object controller) {
        return new Coordinates(cursorX, cursorY);
    }

    public record Coordinates(float x, float y) {}

    public static final class FakeHandedClick {
        // Synthetic KeyBinding state may be stale or mirrored: do NOT read it.
        public boolean leftPressed, rightPressed;
        public final org.vivecraft.client_vr.ClientDataHolderVR.FakeAction action =
            new org.vivecraft.client_vr.ClientDataHolderVR.FakeAction();
        public FakeHandedClick() { action.handed = true; }
        public boolean isDown(ControllerType type) {
            return type == ControllerType.LEFT ? leftPressed : rightPressed;
        }
    }

    public static final class FakeGuiClick {
        public final org.vivecraft.client_vr.ClientDataHolderVR.FakeAction action =
            new org.vivecraft.client_vr.ClientDataHolderVR.FakeAction();
    }

    public static final class FakeGuiShift {
        public boolean leftGripPressed;
        public boolean isDown() { return leftGripPressed; }
    }
}

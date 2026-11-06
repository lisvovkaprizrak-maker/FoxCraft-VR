package dev.foxcraft.keyboard;

import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.gameplay.screenhandlers.GuiHandler;

/** Runs separately with stub Vivecraft classes, never inside game JAR. */
public final class LeftPointerBridgeSmokeTest {
    public static void main(String[] args) {
        System.setProperty("foxcraft.vrBridgeDiagnostics", "true");
        VivecraftLeftPointer pointer = new VivecraftLeftPointer();

        VivecraftLeftPointer.Sample idle = pointer.poll(800, 400);
        require(idle != null && idle.valid(), "Left ray projects to GUI");
        require(idle.x() == 200 && idle.y() == 200, "Left ray uses own UV coordinates");
        require(!idle.pressed(), "Idle controller does not press keys");

        // GUI Shift is unhanded. It may be activated by an unrelated
        // right-controller button, so it cannot count as the left index
        // trigger. This regression test prevents mirrored left clicks.
        GuiHandler.KEY_SHIFT.leftGripPressed = true;
        VivecraftLeftPointer.Sample grip = pointer.poll(800, 400);
        require(grip != null && !grip.pressed(),
            "GUI Shift/grip must not generate phantom left pointer presses");
        GuiHandler.KEY_SHIFT.leftGripPressed = false;

        // The original QuestCraft/Vivecraft per-hand keyboard click is the
        // only active input. Left works on its own native binding.
        GuiHandler.KEY_KEYBOARD_CLICK.leftPressed = true;
        VivecraftLeftPointer.Sample mapped = pointer.poll(800, 400);
        require(mapped != null && mapped.pressed(),
            "Native left keyboard-click must activate left pointer");
        GuiHandler.KEY_KEYBOARD_CLICK.leftPressed = false;

        // A right-hand keyboard click must never appear on the left pointer.
        GuiHandler.KEY_KEYBOARD_CLICK.rightPressed = true;
        VivecraftLeftPointer.Sample right = pointer.poll(800, 400);
        require(right != null && !right.pressed(),
            "Native right keyboard-click must not activate left pointer");
        GuiHandler.KEY_KEYBOARD_CLICK.rightPressed = false;

        GuiHandler.cursorX = -1;
        VivecraftLeftPointer.Sample outside = pointer.poll(800, 400);
        require(outside != null && !outside.valid(), "Offscreen left ray is invalid");

        ClientDataHolderVR.INSTANCE.vr.tracking = false;
        require(pointer.poll(800, 400) == null, "Tracking loss cancels left pointer");
        System.out.println("FoxCraft Vivecraft left-controller bridge smoke tests: OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

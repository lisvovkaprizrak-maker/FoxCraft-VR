package dev.foxcraft.keyboard;

import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.gameplay.screenhandlers.GuiHandler;
import org.vivecraft.client_vr.provider.ControllerType;

/** Runs separately with stub Vivecraft classes, never inside game JAR. */
public final class LeftPointerBridgeSmokeTest {
    public static void main(String[] args) {
        System.setProperty("foxcraft.vrBridgeDiagnostics", "true");
        VivecraftLeftPointer pointer = new VivecraftLeftPointer();
        Object vr = ClientDataHolderVR.INSTANCE.vr;
        Object keyboard = GuiHandler.KEY_KEYBOARD_CLICK;
        Object left = GuiHandler.KEY_LEFT_CLICK;
        Object right = GuiHandler.KEY_RIGHT_CLICK;

        VivecraftLeftPointer.Sample idle = pointer.poll(800, 400);
        require(idle != null && idle.valid(), "Left ray projects to GUI");
        require(idle.x() == 200 && idle.y() == 200, "Left ray uses own UV coordinates");
        require(!idle.pressed(), "Idle controller does not press keys");

        // The Quest 3 bug: right trigger unexpectedly pressed the LEFT ray.
        // HandedKeyBinding.isDown(LEFT) was treated as physical evidence even
        // though its state is synthetic, stale, or mirrored on some forks.
        GuiHandler.KEY_KEYBOARD_CLICK.leftPressed = true;
        GuiHandler.KEY_KEYBOARD_CLICK.rightPressed = false;
        require(!pointer.poll(800, 400).pressed(),
            "Synthetic LEFT isDown must NEVER click without a LEFT physical source");

        // Verify a RIGHT index-trigger action while the synthetic LEFT
        // keyboard click is still stuck true. Only the RIGHT ray may click.
        var guiClick = GuiHandler.KEY_LEFT_CLICK.action;
        guiClick.active = true;
        guiClick.pressed = true;
        guiClick.origin = 200;
        require(FoxCraftGuiClickInput.pressed(vr, ControllerType.RIGHT, keyboard, left, right),
            "Physical RIGHT trigger must click RIGHT ray");
        require(!FoxCraftGuiClickInput.pressed(vr, ControllerType.LEFT, keyboard, left, right),
            "Physical RIGHT trigger must NOT click LEFT ray despite stale isDown");
        require(!pointer.poll(800, 400).pressed(),
            "Physical RIGHT trigger never fires left-pointer fallback");

        // LEFT index-trigger must press the LEFT pointer, independently.
        guiClick.origin = 100;
        require(FoxCraftGuiClickInput.pressed(vr, ControllerType.LEFT, keyboard, left, right),
            "Physical LEFT GUI action clicks LEFT ray");
        require(!FoxCraftGuiClickInput.pressed(vr, ControllerType.RIGHT, keyboard, left, right),
            "Physical LEFT GUI action must NOT click RIGHT ray");
        require(pointer.poll(800, 400).pressed(),
            "Physical LEFT trigger presses left-pointer fallback");

        // A fork can route the left-hand trigger through the other GUI
        // action. This is still accepted only for its true physical origin.
        guiClick.active = false;
        var guiRight = GuiHandler.KEY_RIGHT_CLICK.action;
        guiRight.active = true;
        guiRight.pressed = true;
        guiRight.origin = 100;
        require(pointer.poll(800, 400).pressed(), "LEFT GUI right-click origin can click LEFT keys");
        require(!FoxCraftGuiClickInput.pressed(vr, ControllerType.RIGHT, keyboard, left, right),
            "Left-source GUI right-click must never click RIGHT");
        guiRight.active = false;

        // The native handed Vivecraft action is allowed ONLY with its own
        // active OpenXR state and physical origin, not with KeyBinding.isDown.
        var raw = GuiHandler.KEY_KEYBOARD_CLICK.action;
        raw.active = true;
        raw.leftPressed = true;
        raw.leftOrigin = 100;
        raw.rightPressed = false;
        raw.rightOrigin = 0;
        require(pointer.poll(800, 400).pressed(),
            "Native handed LEFT OpenXR trigger clicks LEFT ray");
        require(!FoxCraftGuiClickInput.pressed(vr, ControllerType.RIGHT, keyboard, left, right),
            "Native handed LEFT trigger cannot click RIGHT ray");

        raw.leftPressed = false;
        raw.leftOrigin = 0;
        raw.rightPressed = true;
        raw.rightOrigin = 200;
        require(!pointer.poll(800, 400).pressed(),
            "Native handed RIGHT trigger cannot click LEFT ray");
        require(FoxCraftGuiClickInput.pressed(vr, ControllerType.RIGHT, keyboard, left, right),
            "Native handed RIGHT trigger clicks RIGHT ray");
        require(raw.hand == ControllerType.RIGHT,
            "Per-hand sample restores Vivecraft's selected hand");

        // Even a corrupt handed state claiming LEFT cannot override origin.
        raw.leftPressed = true;
        raw.leftOrigin = 200;
        raw.rightPressed = false;
        require(!pointer.poll(800, 400).pressed(),
            "Handed LEFT click with RIGHT origin must not click LEFT");

        raw.active = false;
        raw.leftPressed = false;
        GuiHandler.KEY_KEYBOARD_CLICK.leftPressed = false;
        guiClick.active = true;
        guiClick.pressed = true;
        guiClick.origin = 0;
        require(!pointer.poll(800, 400).pressed(),
            "Unknown origin cannot click LEFT");
        guiClick.origin = 100;
        guiClick.active = false;
        require(!pointer.poll(800, 400).pressed(),
            "Inactive physical action cannot click LEFT");
        guiClick.pressed = false;

        // Unhanded GUI Shift/grip is NOT an index trigger.
        GuiHandler.KEY_SHIFT.leftGripPressed = true;
        require(!pointer.poll(800, 400).pressed(),
            "GUI Shift/grip must never become a false LEFT trigger");
        GuiHandler.KEY_SHIFT.leftGripPressed = false;

        GuiHandler.cursorX = -1;
        VivecraftLeftPointer.Sample outside = pointer.poll(800, 400);
        require(outside != null && !outside.valid(), "Offscreen left ray is invalid");

        ClientDataHolderVR.INSTANCE.vr.tracking = false;
        require(pointer.poll(800, 400) == null, "Tracking loss cancels left pointer");
        System.out.println("FoxCraft physical left/right trigger isolation smoke tests: OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

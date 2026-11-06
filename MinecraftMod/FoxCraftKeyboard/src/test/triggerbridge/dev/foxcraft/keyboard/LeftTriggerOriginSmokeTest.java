package dev.foxcraft.keyboard;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import java.util.IdentityHashMap;
import java.util.Map;

/** Fake Vivecraft input-action states, run by CI without a headset. */
public final class LeftTriggerOriginSmokeTest {
    public enum Hand { RIGHT, LEFT }

    public static final class Action {
        public boolean handed, active, right, left;
        public long origin;
        public Hand hand = Hand.RIGHT;
        public boolean isHanded() { return handed; }
        public void setCurrentHand(Hand hand) { this.hand = hand; }
        public Hand getCurrentHand() { return hand; }
        public boolean isActive() { return active; }
        public boolean isButtonPressed() { return handed ?
            (hand == Hand.LEFT ? left : right) : right; }
        public long getLastOrigin() { return origin; }
    }

    public static final class Provider {
        public final Map<KeyBinding, Action> actions = new IdentityHashMap<>();
        public Action getInputAction(KeyBinding key) { return actions.get(key); }
        // Vivecraft MCOpenVR uses PROTECTED, not public. Regression test.
        protected Hand getOriginControllerType(long origin) {
            return switch ((int) origin) {
                case 100 -> Hand.LEFT;
                case 200 -> Hand.RIGHT;
                default -> null;
            };
        }
    }

    public static void main(String[] args) {
        Provider provider = new Provider();
        KeyBinding handedKeyboard = new KeyBinding("keyboard");
        KeyBinding guiClick = new KeyBinding("guiClick");
        Action keyboard = new Action();
        keyboard.handed = true;
        keyboard.active = true;
        keyboard.right = true;
        keyboard.left = false;
        provider.actions.put(handedKeyboard, keyboard);

        Action gui = new Action();
        gui.active = true;
        gui.right = true;
        gui.origin = 200;
        provider.actions.put(guiClick, gui);

        require(!FoxCraftLeftTrigger.pressed(provider, Hand.LEFT,
            handedKeyboard, guiClick), "Right controller trigger must not click left keys");

        require(!FoxCraftLeftTrigger.pressedBinding(provider, Hand.LEFT, guiClick),
            "Unhanded right-only GUI press cannot become left press");
        require(FoxCraftLeftTrigger.pressedBinding(provider, Hand.RIGHT, guiClick),
            "Unhanded right-origin press is accepted only by right hand");
        keyboard.left = true;
        require(FoxCraftLeftTrigger.pressed(provider, Hand.LEFT,
            handedKeyboard, guiClick), "Left-handed raw keyboard action clicks");
        require(keyboard.hand == Hand.RIGHT, "Sampling restores action hand state");
        keyboard.left = false;

        gui.origin = 100;
        require(FoxCraftLeftTrigger.pressed(provider, Hand.LEFT,
            handedKeyboard, guiClick), "Protected left origin must click via GUI action");
        gui.origin = 200;
        require(!FoxCraftLeftTrigger.pressed(provider, Hand.LEFT,
            handedKeyboard, guiClick), "Protected right origin must not click left");

        Action gameUse = new Action();
        gameUse.active = true;
        gameUse.right = true;
        gameUse.origin = 100;
        provider.actions.put(MinecraftClient.getInstance().options.useKey, gameUse);
        require(FoxCraftLeftTrigger.pressed(provider, Hand.LEFT,
            handedKeyboard, guiClick), "Quest left index-use action is accepted");
        gameUse.active = false;
        require(!FoxCraftLeftTrigger.pressed(provider, Hand.LEFT,
            handedKeyboard, guiClick), "Inactive left action does not click");

        // Regression from Quest 3: right trigger used to select the LEFT ray,
        // while left index trigger failed altogether. Verify both directions.
        gui.active = false;
        keyboard.right = true;
        keyboard.left = true; // Simulate a fork that mirrors handed state.
        keyboard.origin = 200;
        require(FoxCraftLeftTrigger.pressedBinding(provider, Hand.RIGHT, handedKeyboard),
            "Right physical-origin keyboard press goes to right ray");
        require(!FoxCraftLeftTrigger.pressedBinding(provider, Hand.LEFT, handedKeyboard),
            "Mirrored handed state must not click the opposite left ray");
        require(keyboard.hand == Hand.RIGHT, "Handed action state is restored");

        keyboard.origin = 100;
        require(FoxCraftLeftTrigger.pressedBinding(provider, Hand.LEFT, handedKeyboard),
            "Left physical-origin keyboard press goes to left ray");
        require(!FoxCraftLeftTrigger.pressedBinding(provider, Hand.RIGHT, handedKeyboard),
            "Mirrored handed state must not click the opposite right ray");

        // On some Quest mappings the keyboard action is absent and the index
        // triggers are bound to ordinary Use/Attack instead.
        keyboard.active = false;
        gameUse.active = true;
        gameUse.origin = 100;
        require(FoxCraftLeftTrigger.pressed(provider, Hand.LEFT,
            handedKeyboard, guiClick), "Left index-use action clicks left ray");
        require(!FoxCraftLeftTrigger.pressed(provider, Hand.RIGHT,
            handedKeyboard, guiClick), "Left index-use cannot click right ray");
        gameUse.origin = 200;
        require(FoxCraftLeftTrigger.pressed(provider, Hand.RIGHT,
            handedKeyboard, guiClick), "Right index-use action clicks right ray");
        require(!FoxCraftLeftTrigger.pressed(provider, Hand.LEFT,
            handedKeyboard, guiClick), "Right index-use cannot click left ray");

        // Both independent rays may have simultaneous physical presses;
        // neither action must be mistaken for the other hand.
        gameUse.active = false;
        keyboard.active = true;
        keyboard.right = true;
        keyboard.left = false;
        keyboard.origin = 200;
        gameUse.origin = 100;
        gameUse.active = true;
        require(FoxCraftLeftTrigger.pressed(provider, Hand.RIGHT,
            handedKeyboard, guiClick), "Right action remains active with left use");
        require(FoxCraftLeftTrigger.pressed(provider, Hand.LEFT,
            handedKeyboard, guiClick), "Left use remains active with right keyboard");
        System.out.println("FoxCraft Quest 3 two-hand trigger origin smoke tests: OK");
    }

    private static void require(boolean pass, String message) {
        if (!pass) throw new AssertionError(message);
    }
}

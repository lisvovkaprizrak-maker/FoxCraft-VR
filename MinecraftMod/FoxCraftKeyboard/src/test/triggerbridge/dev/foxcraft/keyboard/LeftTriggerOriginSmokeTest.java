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
        System.out.println("FoxCraft Quest 3 left index-trigger origin smoke tests: OK");
    }

    private static void require(boolean pass, String message) {
        if (!pass) throw new AssertionError(message);
    }
}

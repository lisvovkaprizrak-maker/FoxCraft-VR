package dev.foxcraft.keyboard;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import java.util.HashSet;
import java.util.Set;

/**
 * Feeds game-key events into Minecraft's normal Keyboard callback. The
 * callback dispatches vanilla controls and modded keybinds, not chat text.
 * Every key-down has a matching key-up, including mode exit and focus loss.
 */
public final class FreeKeyboardGameInput {
    private final Set<Integer> pressed = new HashSet<>();
    private int stickyModifiers;

    public void tapModifier(String name) {
        int bit = FreeKeyboardKeys.modifierBit(name);
        if (bit == 0) return;
        int code = FreeKeyboardKeys.keyCode(name);
        boolean alreadyOn = (stickyModifiers & bit) != 0;
        if (alreadyOn) {
            stickyModifiers &= ~bit;
            send(code, GLFW.GLFW_RELEASE);
        } else {
            stickyModifiers |= bit;
            send(code, GLFW.GLFW_PRESS);
        }
    }

    public boolean modifierOn(String name) {
        return (stickyModifiers & FreeKeyboardKeys.modifierBit(name)) != 0;
    }

    public void press(String name) {
        int key = FreeKeyboardKeys.keyCode(name);
        if (key == -1 || !pressed.add(key)) return;
        send(key, GLFW.GLFW_PRESS);
    }

    public void release(String name) {
        int key = FreeKeyboardKeys.keyCode(name);
        if (key == -1 || !pressed.remove(key)) return;
        send(key, GLFW.GLFW_RELEASE);
    }

    public void releaseAll() {
        // Prevent a stuck F3, movement or function key on panel close.
        for (int key : Set.copyOf(pressed)) {
            pressed.remove(key);
            send(key, GLFW.GLFW_RELEASE);
        }
        // Modifier-down events must be paired even when leaving FREE mode.
        for (String name : new String[]{"CTRL", "SHIFT", "ALT"}) {
            int bit = FreeKeyboardKeys.modifierBit(name);
            if ((stickyModifiers & bit) != 0) {
                stickyModifiers &= ~bit;
                send(FreeKeyboardKeys.keyCode(name), GLFW.GLFW_RELEASE);
            }
        }
    }

    private void send(int key, int action) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) return;
        try {
            client.keyboard.onKey(client.getWindow().getHandle(),
                key, 0, action, stickyModifiers);
        } catch (RuntimeException | LinkageError error) {
            System.err.println("[FoxCraft] Game-key dispatch unavailable: " +
                error.getClass().getSimpleName());
        }
    }
}

package dev.foxcraft.keyboard;

import java.lang.reflect.Method;

/**
 * Best-effort replacement of Vivecraft's original pointer keyboard only while
 * the FoxCraft room-space keyboard has actually rendered. Do not disable the
 * native fallback if our optional VR plane is unavailable.
 */
public final class QuestCraftKeyboardSuppressor {
    private static Method closeMethod;
    private static boolean resolved;
    private static boolean warned;

    private QuestCraftKeyboardSuppressor() {}

    /** Call only after the FoxCraft world quad was rendered successfully. */
    public static void closeWhenFoxCraftVisible() {
        if (!FoxCraftVrPlane.isReady()) return;
        closeNative();
    }

    /** A rendered 2D fallback also permits closing the native VR keyboard. */
    public static void closeWhenScreenFallbackVisible() {
        if (!FoxCraftVrPlane.isVrSession()) return;
        closeNative();
    }

    private static void closeNative() {
        try {
            if (!resolved) {
                // Vivecraft 1.21.5 exposes a public static setter. Reflection
                // keeps compatibility with QuestCraft forks that omit it.
                Class<?> handler = Class.forName(
                    "org.vivecraft.client_vr.gameplay.screenhandlers.KeyboardHandler");
                closeMethod = handler.getMethod("setOverlayShowing", boolean.class);
                resolved = true;
            }
            if (closeMethod != null) {
                // Avoid repeatedly reinitializing the native keyboard state
                // when it is already hidden.
                Class<?> handler = closeMethod.getDeclaringClass();
                boolean showing = handler.getField("SHOWING").getBoolean(null);
                if (showing) closeMethod.invoke(null, false);
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
            resolved = true;
            closeMethod = null;
            if (!warned) {
                warned = true;
                System.err.println("[FoxCraft] Native keyboard close hook unavailable; "
                    + "leaving QuestCraft keyboard unchanged: "
                    + unavailable.getClass().getSimpleName());
            }
        }
    }
}

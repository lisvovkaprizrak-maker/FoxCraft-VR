package dev.foxcraft.keyboard;

import java.lang.reflect.Method;

/** Short, non-blocking Meta Quest controller pulse on a completed keypress. */
public final class FoxCraftHaptics {
    private FoxCraftHaptics() {}

    public static void pulse(int hand, int microseconds) {
        if (hand < 0 || hand > 1 || microseconds <= 0) return;
        try {
            Class<?> type = Class.forName("org.vivecraft.client_vr.ClientDataHolderVR");
            Object holder = type.getMethod("getInstance").invoke(null);
            Object driver = type.getField("vr").get(holder);
            if (driver == null) return;
            Method haptic = driver.getClass()
                .getMethod("triggerHapticPulse", int.class, int.class);
            haptic.invoke(driver, hand, Math.max(100, Math.min(400, microseconds)));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // Keyboard still types normally on headsets without vibration support.
        }
    }
}

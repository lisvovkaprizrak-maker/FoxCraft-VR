package dev.foxcraft.keyboard;

import java.lang.reflect.Method;

/**
 * Reads keyboard and GUI actions WITHOUT changing Vivecraft/QuestCraft button
 * mappings. The stock Keyboard Click action can stop producing presses once
 * the stock keyboard is hidden in favour of FoxCraft's detached surface.
 *
 * GUI click actions are unhanded, so their active OpenXR origin MUST match
 * the physical controller whose ray is being tested. Never assign a global
 * GUI click to BOTH pointers or guess an origin for the left hand.
 */
public final class FoxCraftGuiClickInput {
    private FoxCraftGuiClickInput() {}

    public static boolean pressed(Object provider, Object hand, Object keyboardClick,
                                  Object guiLeftClick, Object guiRightClick) {
        if (provider == null || hand == null) return false;
        return nativeKeyboardClick(hand, keyboardClick) ||
            originatedGuiClick(provider, hand, guiLeftClick) ||
            originatedGuiClick(provider, hand, guiRightClick);
    }

    private static boolean nativeKeyboardClick(Object hand, Object binding) {
        if (binding == null) return false;
        try {
            for (Method method : binding.getClass().getMethods()) {
                if (method.getName().equals("isDown") &&
                    method.getParameterCount() == 1 &&
                    method.getParameterTypes()[0].isInstance(hand)) {
                    return Boolean.TRUE.equals(method.invoke(binding, hand));
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // Unsupported Vivecraft fork: try the GUI actions instead.
        }
        return false;
    }

    private static boolean originatedGuiClick(Object provider, Object hand, Object binding) {
        if (binding == null) return false;
        try {
            Object action = null;
            for (Method method : provider.getClass().getMethods()) {
                if (method.getName().equals("getInputAction") &&
                    method.getParameterCount() == 1 &&
                    method.getParameterTypes()[0].isInstance(binding)) {
                    action = method.invoke(provider, binding);
                    break;
                }
            }
            if (action == null) return false;
            Class<?> type = action.getClass();
            // GUI click must be active and actually held this frame.
            if (!Boolean.TRUE.equals(type.getMethod("isActive").invoke(action)) ||
                !Boolean.TRUE.equals(type.getMethod("isButtonPressed").invoke(action)))
                return false;

            long origin = ((Number) type.getMethod("getLastOrigin")
                .invoke(action)).longValue();
            if (origin == 0L) return false;

            // Vivecraft declares this method protected on its VR provider.
            Method resolve = null;
            for (Class<?> owner = provider.getClass(); owner != null;
                 owner = owner.getSuperclass()) {
                try {
                    resolve = owner.getDeclaredMethod("getOriginControllerType", long.class);
                    break;
                } catch (NoSuchMethodException ignored) {
                    // Search superclass.
                }
            }
            if (resolve == null) return false;
            resolve.setAccessible(true);
            return hand.equals(resolve.invoke(provider, origin));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // Never attribute an unverified GUI event to the other controller.
            return false;
        }
    }
}

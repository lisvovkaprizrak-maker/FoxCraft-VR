package dev.foxcraft.keyboard;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

import java.lang.reflect.Method;
import java.util.Collection;

/**
 * Reads the LEFT index-trigger input from Vivecraft's underlying OpenXR action
 * state, not just Minecraft's GUI click/Shift key state.
 *
 * QuestCraft can hide Vivecraft's built-in keyboard; when it does, the normal
 * KEY_KEYBOARD_CLICK handed KeyBinding may no longer receive synthetic presses.
 * The VRInputAction can still carry the hand-specific digital controller state.
 * Other GUI bindings are checked only when their source is actually LEFT,
 * never using a global right-hand click to activate the left pointer.
 */
public final class FoxCraftLeftTrigger {
    private FoxCraftLeftTrigger() {}

    public static boolean pressed(Object vr, Object leftHand,
                                  Object keyboardClick, Object guiClick) {
        if (vr == null || leftHand == null) return false;
        if (pressedAction(vr, leftHand, keyboardClick)) return true;
        if (pressedAction(vr, leftHand, guiClick)) return true;
        // Some QuestCraft/OpenXR mappings use the ordinary Minecraft trigger
        // binding instead of Vivecraft's keyboard click when a GUI is open.
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.options != null) {
            for (String field : new String[]{"attackKey", "useKey"}) {
                try {
                    Object binding = client.options.getClass().getField(field)
                        .get(client.options);
                    if (pressedAction(vr, leftHand, binding)) return true;
                } catch (ReflectiveOperationException | LinkageError ignored) {}
            }
        }
        return false;
    }

    private static boolean pressedAction(Object vr, Object leftHand,
                                         Object binding) {
        if (!(binding instanceof KeyBinding key)) return false;
        try {
            Method actionMethod = vr.getClass().getMethod(
                "getInputAction", KeyBinding.class);
            Object action = actionMethod.invoke(vr, key);
            if (action == null) return false;

            Class<?> cls = action.getClass();
            boolean handed = (Boolean) cls.getMethod("isHanded").invoke(action);
            if (handed) {
                Class<?> handClass = leftHand.getClass();
                Method current = cls.getMethod("getCurrentHand");
                Method select = cls.getMethod("setCurrentHand", handClass);
                synchronized (action) {
                    Object previous = current.invoke(action);
                    try {
                        select.invoke(action, leftHand);
                        // Raw isActive/isButtonPressed is intentionally used:
                        // native KeyBinding.isDown can be swallowed by the
                        // priority system when the stock keyboard is closed.
                        return (Boolean) cls.getMethod("isActive").invoke(action) &&
                            (Boolean) cls.getMethod("isButtonPressed").invoke(action);
                    } finally {
                        select.invoke(action, previous);
                    }
                }
            }

            if (!(Boolean) cls.getMethod("isActive").invoke(action) ||
                !(Boolean) cls.getMethod("isButtonPressed").invoke(action))
                return false;
            long origin = ((Number) cls.getMethod("getLastOrigin")
                .invoke(action)).longValue();
            if (origin == 0L) return false;
            // Vivecraft's MCOpenVR.getOriginControllerType(long) is
            // PROTECTED, not public. getMethod() silently loses the physical
            // left/right association and makes non-handed GUI/use actions
            // impossible to use for the off-hand index trigger.
            Method originMethod = null;
            Class<?> owner = vr.getClass();
            while (owner != null && originMethod == null) {
                try {
                    originMethod = owner.getDeclaredMethod(
                        "getOriginControllerType", long.class);
                } catch (NoSuchMethodException ignored) {
                    owner = owner.getSuperclass();
                }
            }
            if (originMethod == null) return false;
            originMethod.setAccessible(true);
            Object sourceHand = originMethod.invoke(vr, origin);
            return leftHand.equals(sourceHand);
        } catch (ReflectiveOperationException | RuntimeException |
                 LinkageError ignored) {
            return false;
        }
    }
}

package dev.foxcraft.keyboard;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

import java.lang.reflect.Method;

/**
 * Reads controller-specific OpenXR action states for BOTH VR hands, rather
 * than trusting Minecraft's global GUI click or Shift state.
 *
 * FoxCraft's detached keyboard has a separate pointer per physical hand.
 * A QuestCraft fork may route index triggers through Keyboard Click, GUI
 * Click, Attack or Use. A non-handed action is allowed to fire only from its
 * verified physical source; a handed action is sampled for the requested
 * hand and cross-checked against a reported origin when available.
 * This preserves the old class name for compatibility with the 2D bridge.
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

    /** Check a single Vivecraft binding with a verified controller hand. */
    public static boolean pressedBinding(Object vr, Object hand, Object binding) {
        return vr != null && hand != null && pressedAction(vr, hand, binding);
    }

    private static boolean pressedAction(Object vr, Object requestedHand,
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
                Class<?> handClass = requestedHand.getClass();
                Method current = cls.getMethod("getCurrentHand");
                Method select = cls.getMethod("setCurrentHand", handClass);
                synchronized (action) {
                    Object previous = current.invoke(action);
                    try {
                        select.invoke(action, requestedHand);
                        // Never trust the global KeyBinding.isDown(): it can
                        // reflect a press on the *other* controller.
                        if (!(Boolean) cls.getMethod("isActive").invoke(action) ||
                            !(Boolean) cls.getMethod("isButtonPressed").invoke(action))
                            return false;
                        // Some QuestCraft forks mirror handed button state.
                        // An explicitly different physical origin overrides
                        // that apparent press. An origin of zero is normal on
                        // some handed actions; their per-hand state is enough.
                        long origin = ((Number) cls.getMethod("getLastOrigin")
                            .invoke(action)).longValue();
                        return origin == 0L || matchesHandOrigin(vr, requestedHand, origin);
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
            return origin != 0L && matchesHandOrigin(vr, requestedHand, origin);
        } catch (ReflectiveOperationException | RuntimeException |
                 LinkageError ignored) {
            return false;
        }
    }

    /** getOriginControllerType is protected in Vivecraft's provider class. */
    private static boolean matchesHandOrigin(Object vr, Object hand, long origin)
        throws ReflectiveOperationException
    {
        Method resolver = null;
        Class<?> owner = vr.getClass();
        while (owner != null && resolver == null) {
            try {
                resolver = owner.getDeclaredMethod("getOriginControllerType", long.class);
            } catch (NoSuchMethodException ignored) {
                owner = owner.getSuperclass();
            }
        }
        if (resolver == null) return false;
        resolver.setAccessible(true);
        return hand.equals(resolver.invoke(vr, origin));
    }
}

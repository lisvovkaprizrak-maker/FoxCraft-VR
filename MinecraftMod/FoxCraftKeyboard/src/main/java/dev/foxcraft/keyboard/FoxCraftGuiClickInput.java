package dev.foxcraft.keyboard;

import java.lang.reflect.Method;

/**
 * Samples existing Vivecraft/OpenXR input actions for the physical controller
 * that owns a FoxCraft pointer. This class NEVER changes controller mappings.
 *
 * IMPORTANT: HandedKeyBinding.isDown(LEFT/RIGHT) is a synthetic binding state,
 * not reliable physical input while Vivecraft's own keyboard is hidden.
 * On QuestCraft a RIGHT trigger was making that stale/aliased LEFT binding
 * appear pressed, so FoxCraft clicked the LEFT pointer instead of the RIGHT.
 * Only an active action with a matching OpenXR physical origin may click.
 */
public final class FoxCraftGuiClickInput {
    private FoxCraftGuiClickInput() {}

    /** Absent actions must not take down the entire detached VR keyboard. */
    public static Object optionalBinding(Class<?> owner, String field) {
        try {
            return owner.getField(field).get(null);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    public static boolean pressed(Object provider, Object hand, Object keyboardClick,
                                  Object guiLeftClick, Object guiRightClick) {
        if (provider == null || hand == null) return false;
        // All paths use the SAME origin validation. Do not call isDown()
        // directly even for the special Vivecraft handed keyboard action.
        return pressedFromOrigin(provider, hand, guiLeftClick) ||
            pressedFromOrigin(provider, hand, guiRightClick) ||
            pressedFromOrigin(provider, hand, keyboardClick);
    }

    private static boolean pressedFromOrigin(Object provider, Object hand, Object binding) {
        if (binding == null) return false;
        try {
            Object action = null;
            for (Method m : provider.getClass().getMethods()) {
                if (m.getName().equals("getInputAction") &&
                    m.getParameterCount() == 1 &&
                    m.getParameterTypes()[0].isInstance(binding)) {
                    action = m.invoke(provider, binding);
                    break;
                }
            }
            if (action == null) return false;
            Class<?> cls = action.getClass();
            synchronized (action) {
                boolean handed = Boolean.TRUE.equals(cls.getMethod("isHanded").invoke(action));
                Method setHand = null;
                Object previousHand = null;
                if (handed) {
                    setHand = cls.getMethod("setCurrentHand", hand.getClass());
                    previousHand = cls.getMethod("getCurrentHand").invoke(action);
                    setHand.invoke(action, hand);
                }
                try {
                    if (!Boolean.TRUE.equals(cls.getMethod("isActive").invoke(action)) ||
                        !Boolean.TRUE.equals(cls.getMethod("isButtonPressed").invoke(action)))
                        return false;
                    long origin = ((Number) cls.getMethod("getLastOrigin")
                        .invoke(action)).longValue();
                    // Origin 0 is unknown: it must not generate phantom clicks.
                    if (origin == 0L) return false;
                    return fromHand(provider, origin, hand);
                } finally {
                    if (handed) setHand.invoke(action, previousHand);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false; // Fail closed rather than transfer a press between hands.
        }
    }

    /** Vivecraft/QuestCraft declares getOriginControllerType as protected. */
    private static boolean fromHand(Object provider, long origin, Object expected)
        throws ReflectiveOperationException
    {
        Method resolver = null;
        for (Class<?> owner = provider.getClass(); owner != null;
             owner = owner.getSuperclass()) {
            try {
                resolver = owner.getDeclaredMethod("getOriginControllerType", long.class);
                break;
            } catch (NoSuchMethodException ignored) {
                // Protected Vivecraft method may be in a superclass.
            }
        }
        if (resolver == null) return false;
        resolver.setAccessible(true);
        return expected.equals(resolver.invoke(provider, origin));
    }
}

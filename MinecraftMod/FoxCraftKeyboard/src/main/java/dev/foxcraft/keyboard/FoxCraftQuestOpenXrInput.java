package dev.foxcraft.keyboard;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.BiPredicate;

/**
 * QuestCraft's OpenXR-1.21.5 provider does not expose a reliable origin for
 * ordinary button actions: getOriginControllerType() returns LEFT for any
 * input handle other than its right-hand AIM POSE handle, and its cached
 * handed readBoolean() does not set a subaction path.
 *
 * Sample the existing OpenXR actions with an EXPLICIT /user/hand/{left,right}
 * subaction path. No game/key mappings are created or changed.
 */
public final class FoxCraftQuestOpenXrInput {
    private static final String PROVIDER =
        "org.vivecraft.client_vr.provider.openxr.MCOpenXR";
    private static boolean warned;

    private FoxCraftQuestOpenXrInput() {}

    public static boolean handles(Object provider) {
        return provider != null && PROVIDER.equals(provider.getClass().getName());
    }

    public static boolean pressed(Object provider, Object hand,
                                  Object keyboardClick, Object guiLeftClick,
                                  Object guiRightClick, Object leftUse) {
        if (!handles(provider)) return false;
        return pressedForHand(hand, keyboardClick, guiLeftClick, guiRightClick,
            leftUse, (binding, side) -> sample(provider, binding, side));
    }

    /** Deterministic routing, independently testable without OpenXR hardware. */
    static boolean pressedForHand(Object hand, Object keyboardClick,
                                  Object guiLeftClick, Object guiRightClick,
                                  Object leftUse, BiPredicate<Object, String> sample) {
        if (!(hand instanceof Enum<?> named) || sample == null) return false;
        String side = named.name();
        // Restrict the RIGHT ray to RIGHT trigger actions, and the LEFT ray
        // to LEFT trigger actions. A global (unhanded) action is never reused
        // as evidence that the opposite physical controller was pressed.
        return switch (side) {
            case "RIGHT" -> sampled(sample, guiLeftClick, side) ||
                sampled(sample, keyboardClick, side);
            case "LEFT" -> sampled(sample, keyboardClick, side) ||
                sampled(sample, guiRightClick, side) ||
                sampled(sample, leftUse, side);
            default -> false;
        };
    }

    private static boolean sampled(BiPredicate<Object, String> sample,
                                   Object binding, String side) {
        return binding != null && sample.test(binding, side);
    }

    private static boolean sample(Object provider, Object binding, String side) {
        if (binding == null) return false;
        Object info = null;
        Object state = null;
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
            long handle = action.getClass().getField("handle").getLong(action);
            if (handle == 0L) return false;
            Object actionSet = action.getClass().getField("actionSet").get(action);
            Field setsField = provider.getClass().getDeclaredField("actionSetHandles");
            setsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Object, Long> sets = (Map<Object, Long>) setsField.get(provider);
            Long setHandle = sets.get(actionSet);
            if (setHandle == null || setHandle == 0L) return false;

            Class<?> instanceClass = Class.forName("org.lwjgl.openxr.XrInstance");
            Class<?> sessionClass = Class.forName("org.lwjgl.openxr.XrSession");
            Class<?> setClass = Class.forName("org.lwjgl.openxr.XrActionSet");
            Class<?> actionClass = Class.forName("org.lwjgl.openxr.XrAction");
            Class<?> infoClass = Class.forName("org.lwjgl.openxr.XrActionStateGetInfo");
            Class<?> stateClass = Class.forName("org.lwjgl.openxr.XrActionStateBoolean");
            Class<?> xr = Class.forName("org.lwjgl.openxr.XR10");

            Object instance = provider.getClass().getField("instance").get(provider);
            Object session = provider.getClass().getField("session").get(provider);
            if (instance == null || session == null) return false;
            Object xrSet = setClass.getConstructor(long.class, instanceClass)
                .newInstance(setHandle, instance);
            Object xrAction = actionClass.getConstructor(long.class, setClass)
                .newInstance(handle, xrSet);
            long subaction = ((Number) provider.getClass().getMethod(
                "getPath", String.class).invoke(provider,
                "LEFT".equals(side) ? "/user/hand/left" : "/user/hand/right"))
                .longValue();
            if (subaction == 0L) return false;

            info = infoClass.getMethod("calloc").invoke(null);
            state = stateClass.getMethod("calloc").invoke(null);
            int getInfoType = xr.getField("XR_TYPE_ACTION_STATE_GET_INFO").getInt(null);
            int booleanType = xr.getField("XR_TYPE_ACTION_STATE_BOOLEAN").getInt(null);
            infoClass.getMethod("type", int.class).invoke(info, getInfoType);
            infoClass.getMethod("action", actionClass).invoke(info, xrAction);
            infoClass.getMethod("subactionPath", long.class).invoke(info, subaction);
            stateClass.getMethod("type", int.class).invoke(state, booleanType);

            int result = ((Number) xr.getMethod("xrGetActionStateBoolean",
                sessionClass, infoClass, stateClass)
                .invoke(null, session, info, state)).intValue();
            return result == 0 &&
                Boolean.TRUE.equals(stateClass.getMethod("isActive").invoke(state)) &&
                Boolean.TRUE.equals(stateClass.getMethod("currentState").invoke(state));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            if (!warned) {
                warned = true;
                System.err.println("[FoxCraft][QXR-hand-v1] Direct physical OpenXR " +
                    "hand input unavailable: " + ex.getClass().getSimpleName() +
                    " (" + ex.getMessage() + ")");
            }
            return false;
        } finally {
            closeQuietly(state);
            closeQuietly(info);
        }
    }

    private static void closeQuietly(Object struct) {
        if (struct == null) return;
        try {
            struct.getClass().getMethod("close").invoke(struct);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Only best-effort cleanup of temporary native structs.
        }
    }
}

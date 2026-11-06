package dev.foxcraft.keyboard;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Optional second-hand cursor for Vivecraft 1.21.5. Uses the same public GUI
 * ray/plane geometry as Vivecraft, so the left-hand hit point is NOT the
 * desktop mouse cursor controlled by the right hand.
 *
 * Reflection makes the Fabric mod safe to load without a specific Vivecraft
 * Java build. In non-VR environments poll() returns null.
 */
public final class VivecraftLeftPointer {
    public record Sample(boolean valid, double x, double y, boolean pressed) {}

    private boolean unavailable;

    public Sample poll(int guiWidth, int guiHeight) {
        if (unavailable) return null;
        try {
            Class<?> holderClass = Class.forName("org.vivecraft.client_vr.ClientDataHolderVR");
            Object holder = holderClass.getMethod("getInstance").invoke(null);
            Object provider = get(holder, "vr");
            Object player = get(holder, "vrPlayer");
            if (provider == null || player == null) return null;

            // Vivecraft's tracked left controller uses index 1.
            Method tracking = provider.getClass().getMethod("isControllerTracking", int.class);
            if (!(Boolean) tracking.invoke(provider, 1)) return null;

            Class<?> gui = Class.forName(
                "org.vivecraft.client_vr.gameplay.screenhandlers.GuiHandler");
            Object guiPosition = gui.getField("GUI_POS_ROOM").get(null);
            Object guiRotation = gui.getField("GUI_ROTATION_ROOM").get(null);
            if (guiPosition == null || guiRotation == null) return null;
            float guiScale = gui.getField("GUI_SCALE").getFloat(null);

            Object vrData = get(player, "vrdata_room_pre");
            if (vrData == null) return null;
            Object controller = vrData.getClass().getMethod("getController", int.class)
                .invoke(vrData, 1);
            if (controller == null) return null;

            Method cursorMethod = null;
            for (Method m : gui.getMethods()) {
                if (m.getName().equals("getTexCoordsForCursor") &&
                    m.getParameterCount() == 4) {
                    cursorMethod = m;
                    break;
                }
            }
            if (cursorMethod == null) {
                unavailable = true;
                return null;
            }
            Object coords = cursorMethod.invoke(null,
                guiPosition, guiRotation, guiScale, controller);
            double u = ((Number) coords.getClass().getMethod("x").invoke(coords)).doubleValue();
            double v = ((Number) coords.getClass().getMethod("y").invoke(coords)).doubleValue();

            Object keyboardBinding = gui.getField("KEY_KEYBOARD_CLICK").get(null);
            Class<?> handType = Class.forName("org.vivecraft.client_vr.provider.ControllerType");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object leftHand = Enum.valueOf((Class<? extends Enum>) handType.asSubclass(Enum.class),
                "LEFT");
            // The detached panel and 2D fallback use identical, origin-
            // verified click rules. No shift/grip or game-action remapping.
            Object guiLeftClick = FoxCraftGuiClickInput.optionalBinding(gui, "KEY_LEFT_CLICK");
            Object guiRightClick = FoxCraftGuiClickInput.optionalBinding(gui, "KEY_RIGHT_CLICK");
            boolean nativeLeftClick = FoxCraftGuiClickInput.pressed(
                provider, leftHand, keyboardBinding, guiLeftClick, guiRightClick);
            boolean valid = u >= 0 && u <= 1 && v >= 0 && v <= 1;
            return new Sample(valid, u * guiWidth, v * guiHeight,
                nativeLeftClick);
        } catch (ClassNotFoundException e) {
            unavailable = true;
            return null;
        } catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
            // Opt-in diagnostics for the standalone bridge test only.
            if (Boolean.getBoolean("foxcraft.vrBridgeDiagnostics"))
                e.printStackTrace();
            // Some QuestCraft forks change Vivecraft internals. Never break
            // the already-tested right-controller path if reflection differs.
            unavailable = true;
            return null;
        }
    }

    private static Object get(Object obj, String field) throws ReflectiveOperationException {
        Field f = obj.getClass().getField(field);
        return f.get(obj);
    }
}

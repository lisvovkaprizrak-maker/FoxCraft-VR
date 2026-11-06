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
            boolean trigger = (Boolean) keyboardBinding.getClass()
                .getMethod("isDown", handType).invoke(keyboardBinding, leftHand);
            // The standalone plane and the emergency GUI overlay must read
            // the SAME physical left index trigger (Quest 3: Place / Use).
            Object leftGuiClick = null;
            try {
                leftGuiClick = gui.getField("KEY_LEFT_CLICK").get(null);
            } catch (NoSuchFieldException ignored) {
                // Older QuestCraft variants/test doubles may not expose
                // the optional GUI left-click binding at all.
            }
            // Keep this optional reflection link: the standalone geometry
            // smoke tests compile VivecraftLeftPointer without Minecraft,
            // whereas the actual Fabric mod includes FoxCraftLeftTrigger.
            // No right-hand clicks can activate this left-hand path.
            Object rightGuiClick = null;
            try { rightGuiClick = gui.getField("KEY_RIGHT_CLICK").get(null); }
            catch (NoSuchFieldException ignored) { }
            boolean rawLeftTrigger = rawTrigger(provider, leftHand,
                keyboardBinding, leftGuiClick) ||
                (rightGuiClick != null && rawTrigger(provider, leftHand,
                    keyboardBinding, rightGuiClick));

            // Stock Oculus GUI bindings map left grip to GUI Shift, while
            // only right trigger clicks the mouse. Left grip is a useful
            // fallback until left trigger is bound to Keyboard Click in VR.
            Object shiftBinding = gui.getField("KEY_SHIFT").get(null);
            boolean leftGrip = (Boolean) shiftBinding.getClass()
                .getMethod("isDown").invoke(shiftBinding);
            boolean valid = u >= 0 && u <= 1 && v >= 0 && v <= 1;
            return new Sample(valid, u * guiWidth, v * guiHeight,
                trigger || rawLeftTrigger || leftGrip);
        } catch (ClassNotFoundException e) {
            unavailable = true;
            return null;
        } catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
            // Some QuestCraft forks change Vivecraft internals. Never break
            // the already-tested right-controller path if reflection differs.
            unavailable = true;
            return null;
        }
    }

    private static boolean rawTrigger(Object provider, Object leftHand,
                                      Object keyboardClick, Object guiClick) {
        try {
            Class<?> helper = Class.forName("dev.foxcraft.keyboard.FoxCraftLeftTrigger");
            return (Boolean) helper.getMethod("pressed",
                Object.class, Object.class, Object.class, Object.class)
                .invoke(null, provider, leftHand, keyboardClick, guiClick);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static Object get(Object obj, String field) throws ReflectiveOperationException {
        Field f = obj.getClass().getField(field);
        return f.get(obj);
    }
}

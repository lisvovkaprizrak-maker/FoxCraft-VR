package dev.foxcraft.keyboard;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Independent Vivecraft room-space surface, placed BETWEEN chat and the
 * original QuestCraft keyboard. Never modifies KeyboardHandler.UI, SHOWING,
 * FRAMEBUFFER, native key layouts or Vivecraft's controller bindings.
 *
 * This optional bridge is deliberately fail-closed: when the expected
 * Vivecraft 1.21.5 hooks are absent, vanilla screen-embedded FoxCraft remains.
 */
public final class FoxCraftVrPlane {
    private static final float SCALE_MULTIPLIER = 0.68f;
    // Use the same chat-relative placement rule as Vivecraft's own keyboard.
    // FoxCraft is a separate layer: the original keyboard is never disabled.
    private static final float BELOW_CHAT = 0.69f;
    private static final float FORWARD_FROM_CHAT = 0.28f;
    private static final float TOWARDS_PLAYER = 0.065f;

    private static VirtualKeyboardOverlay keyboard;
    private static PanelScreen screen;
    private static Framebuffer target;
    private static int bufferWidth, bufferHeight;
    private static int uiWidth, uiHeight;
    private static float scale;
    private static Vector3f position;
    private static Matrix4f rotation;
    private static boolean ready;
    // If the Vivecraft world-space draw hook never fires on a fork, the
    // original on-screen keyboard stays available instead of disappearing.
    private static boolean worldHookWorking;
    private static boolean disabled;

    // One ray and one press latch per controller; swipe paths stay separate.
    private static final boolean[] wasPressed = new boolean[2];
    private static final double[] cursorX = new double[]{-100, -100};
    private static final double[] cursorY = new double[]{-100, -100};

    private FoxCraftVrPlane() {}

    public static void attach(VirtualKeyboardOverlay overlay) {
        keyboard = overlay;
    }

    /** Used only by the ordinary 2D Screen renderer to avoid duplicate panels. */
    public static boolean isReady() {
        return ready && worldHookWorking && !disabled;
    }

    /** Prevent secondary 2D chat clicks while pointing at FoxCraft in VR. */
    public static boolean interceptsMainGuiMouse() {
        return isReady() && keyboard != null &&
            keyboard.detachedContains(cursorX[0], cursorY[0]);
    }

    /**
     * Called before Vivecraft begins rendering its stereo eye passes. This
     * draws into a *different* framebuffer from QuestCraft's own keyboard.
     */
    public static void prepareFrame() {
        ready = false;
        if (keyboard == null || disabled) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (!(client.currentScreen instanceof ChatScreen)) {
            wasPressed[0] = wasPressed[1] = false;
            releaseBuffer();
            return;
        }
        Framebuffer old = null;
        try {
            // IMPORTANT: KeyboardHandler.SHOWING is false when the player sees
            // "Click to open keyboard". FoxCraft must still appear below chat.
            // Anchor to the CHAT GUI, not the native keyboard visibility.
            Class<?> guiClass = Class.forName(
                "org.vivecraft.client_vr.gameplay.screenhandlers.GuiHandler");
            Object chatPos = guiClass.getField("GUI_POS_ROOM").get(null);
            Object chatRotation = guiClass.getField("GUI_ROTATION_ROOM").get(null);
            if (!(chatPos instanceof Vector3f guiPos) ||
                !(chatRotation instanceof Matrix4f guiRot))
                return; // GUI is not yet positioned; retry on the next frame.

            float guiScale = guiClass.getField("GUI_SCALE").getFloat(null);
            if (!(guiScale > 0)) return;
            // Equivalent to QuestCraft's chat-relative keyboard positioning:
            // project down the GUI's local up axis and slightly toward player.
            // Neither KeyboardHandler.SHOWING nor its framebuffer is modified.
            rotation = new Matrix4f(guiRot)
                .rotateX((float) Math.toRadians(-30.0));
            position = new Vector3f(guiPos)
                .sub(guiRot.transformDirection(new Vector3f(0, 1, 0),
                    new Vector3f()).mul(BELOW_CHAT * guiScale))
                .add(guiRot.transformDirection(new Vector3f(0, 0, 1),
                    new Vector3f()).mul(FORWARD_FROM_CHAT * guiScale))
                .add(rotation.transformDirection(new Vector3f(0, 0, 1),
                    new Vector3f()).mul(TOWARDS_PLAYER * guiScale));
            scale = Math.max(0.25f, guiScale * SCALE_MULTIPLIER);
            int w = guiClass.getField("GUI_WIDTH").getInt(null);
            int h = guiClass.getField("GUI_HEIGHT").getInt(null);
            int sw = guiClass.getField("SCALED_WIDTH_MAX").getInt(null);
            int sh = guiClass.getField("SCALED_HEIGHT_MAX").getInt(null);
            if (w <= 0 || h <= 0 || sw < 220 || sh < 140) return;
            if (target == null || bufferWidth != w || bufferHeight != h) {
                releaseBuffer();
                target = new SimpleFramebuffer("FoxCraft VR keyboard", w, h, true);
                bufferWidth = w;
                bufferHeight = h;
            }
            if (screen == null || sw != uiWidth || sh != uiHeight) {
                screen = new PanelScreen();
                screen.init(client, sw, sh);
                uiWidth = sw;
                uiHeight = sh;
            }

            updateControllers(client.currentScreen, guiClass);
            // Saves original renderer so QuestCraft's own keyboard can draw
            // immediately afterwards without its framebuffer being stolen.
            old = client.getFramebuffer();
            ((VrFramebufferAccess) client).foxcraft$setRenderTarget(target);
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                target.getColorAttachment(), 0x00000000,
                target.getDepthAttachment(), 1.0);
            DrawContext draw = new DrawContext(client,
                client.getBufferBuilders().getEntityVertexConsumers());

            // Vivecraft sets the correct max-scale GUI projection and GPU
            // state here. Call its public helper rather than duplicating it.
            Class<?> helper = Class.forName(
                "org.vivecraft.client_vr.render.helpers.RenderHelper");
            Method render = null;
            for (Method method : helper.getMethods()) {
                if (method.getName().equals("drawScreen") &&
                    method.getParameterCount() == 4) {
                    render = method;
                    break;
                }
            }
            if (render == null) throw new NoSuchMethodException("RenderHelper.drawScreen");
            render.invoke(null, draw, client.getRenderTickCounter(), screen, true);
            ready = true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            // QuestCraft versions differ. Never crash Minecraft or remove the
            // stock VR keyboard when our optional 3D integration fails.
            disabled = true;
            releaseBuffer();
            System.err.println("[FoxCraft] Separate VR keyboard unavailable: "
                + error.getClass().getSimpleName() + ": " + error.getMessage());
        } finally {
            if (old != null)
                ((VrFramebufferAccess) client).foxcraft$setRenderTarget(old);
        }
    }

    /**
     * Called after native GUI & QuestCraft keyboard world quads are rendered,
     * once for each stereo eye. It adds our quad but never hides theirs.
     */
    public static void renderWorld(float partialTick, boolean depthAlways) {
        if (!ready || target == null || position == null || rotation == null) return;
        try {
            Class<?> helper = Class.forName(
                "org.vivecraft.client_vr.render.helpers.VREffectsHelper");
            Class<?> gui = Class.forName(
                "org.vivecraft.client_vr.gameplay.screenhandlers.GuiHandler");
            Method draw = null;
            for (Method method : helper.getMethods()) {
                if (method.getName().equals("render2D") &&
                    method.getParameterCount() == 5) {
                    draw = method;
                    break;
                }
            }
            if (draw == null) throw new NoSuchMethodException("VREffectsHelper.render2D");
            Field scaleField = gui.getField("GUI_SCALE");
            float previous = scaleField.getFloat(null);
            try {
                scaleField.setFloat(null, scale);
                draw.invoke(null, partialTick, target, position, rotation, depthAlways);
                worldHookWorking = true;
            } finally {
                scaleField.setFloat(null, previous);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            disabled = true;
            ready = false;
            System.err.println("[FoxCraft] VR surface rendering disabled: "
                + error.getClass().getSimpleName());
        }
    }

    private static void updateControllers(Screen original, Class<?> guiClass)
        throws ReflectiveOperationException
    {
        Object holder = Class.forName("org.vivecraft.client_vr.ClientDataHolderVR")
            .getMethod("getInstance").invoke(null);
        Object player = holder.getClass().getField("vrPlayer").get(holder);
        Object provider = holder.getClass().getField("vr").get(holder);
        Object data = player.getClass().getField("vrdata_room_pre").get(player);
        Class<?> handType = Class.forName("org.vivecraft.client_vr.provider.ControllerType");
        Object keyboardClick = guiClass.getField("KEY_KEYBOARD_CLICK").get(null);
        Object regularClick = guiClass.getField("KEY_LEFT_CLICK").get(null);
        Object guiShift = guiClass.getField("KEY_SHIFT").get(null);
        Method cursorProject = null;
        for (Method method : guiClass.getMethods()) {
            if (method.getName().equals("getTexCoordsForCursor") &&
                method.getParameterCount() == 4) cursorProject = method;
        }
        if (cursorProject == null)
            throw new NoSuchMethodException("GuiHandler.getTexCoordsForCursor");
        for (int hand = 0; hand < 2; hand++) {
            boolean tracked = (Boolean) provider.getClass()
                .getMethod("isControllerTracking", int.class).invoke(provider, hand);
            double x = -100, y = -100;
            if (tracked) {
                Object controller = data.getClass()
                    .getMethod("getController", int.class).invoke(data, hand);
                Object coords = cursorProject.invoke(null,
                    position, rotation, scale, controller);
                double u = ((Number) coords.getClass().getMethod("x").invoke(coords))
                    .doubleValue();
                double v = ((Number) coords.getClass().getMethod("y").invoke(coords))
                    .doubleValue();
                if (u >= 0 && u <= 1 && v >= 0 && v <= 1) {
                    x = u * uiWidth;
                    y = v * uiHeight;
                }
            }
            cursorX[hand] = x;
            cursorY[hand] = y;
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object handed = Enum.valueOf(
                (Class<? extends Enum>) handType.asSubclass(Enum.class),
                hand == 0 ? "RIGHT" : "LEFT");
            boolean downKeyboard = (Boolean) keyboardClick.getClass()
                .getMethod("isDown", handType).invoke(keyboardClick, handed);
            boolean downFallback = ((KeyBinding) (hand == 0 ? regularClick : guiShift))
                .isPressed();
            boolean down = tracked && (downKeyboard || downFallback);
            if (down && !wasPressed[hand] && x >= 0 && y >= 0)
                keyboard.vrPressed(original, x, y, hand);
            else if (!down && wasPressed[hand])
                keyboard.vrReleased(original, x, y, hand);
            else if (down)
                keyboard.vrMoved(original, x, y, hand);
            wasPressed[hand] = down;
        }
    }

    private static void releaseBuffer() {
        if (target != null) {
            target.delete();
            target = null;
        }
        screen = null;
        position = null;
        rotation = null;
        bufferWidth = bufferHeight = 0;
    }

    /** Never installed as MinecraftClient.currentScreen. */
    private static final class PanelScreen extends Screen {
        PanelScreen() { super(Text.empty()); }

        @Override
        public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.currentScreen instanceof ChatScreen actual && keyboard != null)
                keyboard.renderDetached(actual, draw,
                    (int) cursorX[0], (int) cursorY[0], this.width, this.height,
                    cursorX[1], cursorY[1]);
        }
    }
}

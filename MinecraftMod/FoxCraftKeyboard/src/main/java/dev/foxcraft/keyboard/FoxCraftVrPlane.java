package dev.foxcraft.keyboard;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Independent Vivecraft room-space surface, placed below the current
 * Minecraft text GUI (chat or menu), separate from the QuestCraft keyboard. Never modifies KeyboardHandler.UI, SHOWING,
 * FRAMEBUFFER, native key layouts or Vivecraft's controller bindings.
 *
 * This optional bridge is deliberately fail-closed: when the expected
 * Vivecraft 1.21.5 hooks are absent, vanilla screen-embedded FoxCraft remains.
 */
public final class FoxCraftVrPlane {
    // Larger physical quad so anti-aliased keys are legible at normal Quest distance.
// This changes only room-space size, not the fixed logical key layout.
    private static final float SCALE_MULTIPLIER = 1.05f;
    // Draw the same logical 390px keyboard into a higher-resolution VR
    // framebuffer. The quad's physical scale and controller hit coordinates
    // remain unchanged; only its raster sampling density improves.
    // Keep supersampling moderate on mobile VR GPUs and limit framebuffer
    // sizes to avoid running out of texture memory in QuestCraft.
    private static final float HD_RENDER_SCALE = 1.5f;
    // In menu, keep the detached keyboard BELOW the GUI, not across the
    // world/server list. Chat keeps the user-tested original placement.
    private static final float MENU_SCALE_MULTIPLIER = 0.73f;
    private static final float BELOW_MENU = 1.02f;
    private static final float MENU_FORWARD = 0.10f;
    // FoxCraft is a separate layer: the native keyboard remains a fallback.
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

    /** Actual Vivecraft GUI projection, not the cosmetic beam direction. */
    public static boolean isPointingAtKeyboard(int hand) {
        // Do not gate this by isReady(): that requires a completed world quad
        // draw, but controller rays may be rendered earlier in the eye pass.
        if (hand < 0 || hand >= 2 || keyboard == null || disabled ||
            !isKeyboardActive() || position == null || rotation == null ||
            uiWidth <= 0 || uiHeight <= 0) return false;
        return keyboard.detachedContains(cursorX[hand], cursorY[hand]);
    }

    private FoxCraftVrPlane() {}

    public static void attach(VirtualKeyboardOverlay overlay) {
        keyboard = overlay;
    }

    /** Used only by the ordinary 2D Screen renderer to avoid duplicate panels. */
    public static boolean isReady() {
        return ready && worldHookWorking && !disabled;
    }

    /** True only while Vivecraft is actually rendering a VR headset session. */
    public static boolean isVrSession() {
        try {
            Class<?> vr = Class.forName("org.vivecraft.client_vr.VRState");
            return vr.getField("VR_RUNNING").getBoolean(null);
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            return false;
        }
    }

    /**
     * Beams are visible only during an active FoxCraft text-editing session,
     * never in the regular game, world lists or other non-editing menus.
     * The same focus/request rule drives both detached and 2D keyboards.
     */
    public static boolean isKeyboardActive() {
        if (!isVrSession() || keyboard == null) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        return keyboard.shouldDisplayForScreen(client.currentScreen);
    }

    /**
     * Returns the real Vivecraft keyboard hit point in render-eye-relative world
     * coordinates. The projection uses the same cursor UV, room panel pose,
     * quad size and VR world transform as GUI input and VREffectsHelper.render2D.
     * Null means that the controller did not hit our panel this frame.
     */
    public static Vector3f controllerHitRelativeToEye(int hand) {
        if (!isPointingAtKeyboard(hand) || position == null || rotation == null
            || uiWidth <= 0 || uiHeight <= 0) return null;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            float u = (float) (cursorX[hand] / uiWidth);
            float v = (float) (cursorY[hand] / uiHeight);
            float aspect = (float) mc.getWindow().getScaledHeight()
                / (float) mc.getWindow().getScaledWidth();
            Vector3f inRoom = rotation.transformPosition(new Vector3f(
                (u - 0.5f) * 1.5f * scale,
                (0.5f - v) * 1.5f * scale * aspect, 0.0f))
                .add(position);

            Object holder = Class.forName("org.vivecraft.client_vr.ClientDataHolderVR")
                .getMethod("getInstance").invoke(null);
            Object player = holder.getClass().getField("vrPlayer").get(holder);
            Object world = player.getClass().getField("vrdata_world_render").get(player);
            Class<?> worldClass = world.getClass();
            float worldScale = worldClass.getField("worldScale").getFloat(world);
            float yaw = worldClass.getField("rotation_radians").getFloat(world);
            inRoom.mul(worldScale).rotateY(yaw);
            Object origin = worldClass.getField("origin").get(world);
            Object pass = holder.getClass().getField("currentPass").get(holder);
            Method getEye = null;
            for (Method method : worldClass.getMethods())
                if (method.getName().equals("getEye") &&
                    method.getParameterCount() == 1) getEye = method;
            if (getEye == null) return null;
            Object eyePose = getEye.invoke(world, pass);
            Object eye = eyePose.getClass().getMethod("getPosition").invoke(eyePose);
            Class<?> vec = origin.getClass();
            return new Vector3f(
                inRoom.x + (float) (vec.getField("x").getDouble(origin)
                    - vec.getField("x").getDouble(eye)),
                inRoom.y + (float) (vec.getField("y").getDouble(origin)
                    - vec.getField("y").getDouble(eye)),
                inRoom.z + (float) (vec.getField("z").getDouble(origin)
                    - vec.getField("z").getDouble(eye)));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null; // Never render a guessed beam through the world.
        }
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
        if (!keyboard.shouldDisplayForScreen(client.currentScreen)) {
            wasPressed[0] = wasPressed[1] = false;
            ready = false;
            releaseBuffer();
            return;
        }
        Framebuffer old = null;
        int[] oldViewport = null;
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
            boolean inChat = client.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen;
            float down = inChat ? BELOW_CHAT : BELOW_MENU;
            float forward = inChat ? FORWARD_FROM_CHAT : MENU_FORWARD;
            rotation = new Matrix4f(guiRot)
                .rotateX((float) Math.toRadians(-30.0));
            position = new Vector3f(guiPos)
                .sub(guiRot.transformDirection(new Vector3f(0, 1, 0),
                    new Vector3f()).mul(down * guiScale))
                .add(guiRot.transformDirection(new Vector3f(0, 0, 1),
                    new Vector3f()).mul(forward * guiScale))
                .add(rotation.transformDirection(new Vector3f(0, 0, 1),
                    new Vector3f()).mul(TOWARDS_PLAYER * guiScale));
            scale = Math.max(0.25f, guiScale *
                (inChat ? SCALE_MULTIPLIER : MENU_SCALE_MULTIPLIER));
            int w = guiClass.getField("GUI_WIDTH").getInt(null);
            int h = guiClass.getField("GUI_HEIGHT").getInt(null);
            int sw = guiClass.getField("SCALED_WIDTH_MAX").getInt(null);
            int sh = guiClass.getField("SCALED_HEIGHT_MAX").getInt(null);
            if (w <= 0 || h <= 0 || sw < 220 || sh < 140) return;
            // Supersample both axes to keep the same world-space aspect ratio.
            // GUI_WIDTH/HEIGHT are pixel framebuffer dimensions; SCALED_WIDTH_MAX
            // and SCALED_HEIGHT_MAX remain the logical GUI layout dimensions.
            float hdScale = Math.max(1.0f,
                Math.min(HD_RENDER_SCALE, 4096.0f / Math.max(w, h)));
            int hdWidth = Math.round(w * hdScale);
            int hdHeight = Math.round(h * hdScale);
            if (target == null || bufferWidth != hdWidth || bufferHeight != hdHeight) {
                // Do NOT call releaseBuffer() here: that method also resets
                // position and rotation computed just above. The first VR
                // frame would then project a ray with NULL geometry, disable
                // FoxCraft's entire VR bridge, and never display the panel.
                if (target != null) target.delete();
                target = null;
                screen = null; // Reinitialise the panel at the new GUI size.
                target = new SimpleFramebuffer("FoxCraft VR keyboard HD",
                    hdWidth, hdHeight, true);
                // Smooth bilinear sampling of the actual in-game VR surface,
                // not just a separate marketing mock-up.
                target.getColorAttachment().setTextureFilter(
                    FilterMode.LINEAR, FilterMode.LINEAR, false);
                bufferWidth = hdWidth;
                bufferHeight = hdHeight;
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
            // RenderHelper draws the unchanged GUI geometry to our
            // double-resolution target; explicit viewport avoids relying on
            // the window's lower-resolution eye/GUI framebuffer size.
            oldViewport = new int[4];
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, oldViewport);
            GL11.glViewport(0, 0, hdWidth, hdHeight);
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
            // VR stereo eye and desktop GUI must inherit their exact previous
            // viewport, not our supersampled offscreen keyboard dimensions.
            if (oldViewport != null)
                GL11.glViewport(oldViewport[0], oldViewport[1],
                    oldViewport[2], oldViewport[3]);
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
                // Close stock Vivecraft keyboard only after our separate VR
                // surface genuinely rendered. Otherwise retain its fallback.
                QuestCraftKeyboardSuppressor.closeWhenFoxCraftVisible();
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
            // GUI Shift is a modifier, not the left-hand trigger.
            // Treating it as a click steals input and makes the off hand
            // appear to be the only active controller.
            // Preserve the normal Vivecraft right-hand GUI click binding.
            boolean downRightGui = hand == 0 && ((KeyBinding) regularClick).isPressed();
            boolean down = tracked && (downKeyboard || downRightGui);
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
            if (keyboard != null && keyboard.shouldDisplayForScreen(client.currentScreen))
                keyboard.renderDetached(client.currentScreen, draw,
                    (int) cursorX[0], (int) cursorY[0], this.width, this.height,
                    cursorX[1], cursorY[1]);
        }
    }
}

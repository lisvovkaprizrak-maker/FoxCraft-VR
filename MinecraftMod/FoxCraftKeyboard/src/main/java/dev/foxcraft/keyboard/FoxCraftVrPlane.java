package dev.foxcraft.keyboard;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.MinecraftClient;
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
    // Use an INTEGER factor because RenderHelper.drawScreen takes Vivecraft's
    // GUI_SCALE_FACTOR_MAX as an integer. Arbitrary 1.5x offscreen sizes used
    // to make the UI appear smaller and offset relative to controller UVs.
    private static final int HD_RENDER_SCALE = 2;
    private static final int MAX_HD_TARGET_SIDE = 3072;
    // In menu, keep the detached keyboard BELOW the GUI, not across the
    // world/server list. Chat keeps the user-tested original placement.
    private static final float MENU_SCALE_MULTIPLIER = 0.73f;
    private static final float BELOW_MENU = 1.02f;
    private static final float MENU_FORWARD = 0.10f;
    // FoxCraft is a separate layer: the native keyboard remains a fallback.
    // Place the keyboard clear of the chat history and input, even after reset.
    private static final float BELOW_CHAT = 1.05f;
    private static final float FORWARD_FROM_CHAT = 0.28f;
    private static final float TOWARDS_PLAYER = 0.065f;
    // Vivecraft MathUtils.LEFT is local +X. Shift toward the player's RIGHT
    // with -X and downward with -Y in the *original GUI's room basis*.
    // Both the rendered quad and getTexCoordsForCursor use this exact pose.
    // These are small intentional comfort offsets, NOT substitutes for
    // framebuffer alignment. Before the exact HD GUI-scale fix above, much
    // larger offsets attempted to compensate for rasterization in the
    // upper-left quadrant and would now push the corrected panel too far.
    private static final float CHAT_RIGHT_SHIFT = 0.02f;
    private static final float CHAT_DOWN_SHIFT = 0.03f;
    private static final float MENU_RIGHT_SHIFT = 0.01f;
    private static final float MENU_DOWN_SHIFT = 0.02f;

    private static VirtualKeyboardOverlay keyboard;
    private static PanelScreen screen;
    private static Framebuffer target;
    private static int bufferWidth, bufferHeight;
    private static int uiWidth, uiHeight;
    private static float scale;
    private static Vector3f position;
    private static Matrix4f rotation;
    // Capture the chat/menu GUI pose ONCE per editing screen. Re-reading
    // GUI_POS_ROOM every frame makes the supposedly free keyboard follow chat.
    private static Screen placementScreen;
    private static Vector3f placementAnchor;
    private static Matrix4f placementRotation;
    private static boolean ready;
    private static int grabHand = -1;
    private static Vector3f grabStartController;
    private static float grabStartRight, grabStartDown, grabStartForward;
    private static float grabStartHistory;
    private static int grabStartInputY;
    private static String grabTarget = "KEYBOARD";

    public static FoxCraftFeatureSettings features() {
        return keyboard != null ? keyboard.features() : null;
    }

    /** Recenter only the keyboard surface; never move the chat itself. */
    public static void resetKeyboardPlacement() {
        finishGrab(grabHand);
        placementScreen = null;
        placementAnchor = null;
        placementRotation = null;
    }

    /**
     * VR grab mode starts only on the explicit MOVE_GRAB button, never on an
     * ordinary typing key. Controller origin is in Vivecraft room coordinates.
     */
    public static void beginGrab(int hand) {
        beginGrab(hand, "KEYBOARD");
    }

    public static void beginGrab(int hand, String target) {
        // Dragging is restricted to an explicitly pressed right-trigger
        // move button. Ordinary keyboard typing remains unchanged.
        if (hand != 0 || keyboard == null ||
            keyboard.features().positionLocked()) return;
        if (!"KEYBOARD".equals(target) && !"INPUT".equals(target) &&
            !"HISTORY".equals(target) && !"FREE".equals(target)) return;
        Vector3f controller = controllerRoomPosition(hand);
        if (controller == null) return;
        FoxCraftFeatureSettings pref = keyboard.features();
        grabHand = hand;
        grabStartController = controller;
        grabStartRight = "FREE".equals(target) ? pref.freeRight() : pref.offsetRight();
        grabStartDown = "FREE".equals(target) ? pref.freeDown() : pref.offsetDown();
        grabStartForward = "FREE".equals(target) ? pref.freeForward() : pref.offsetForward();
        grabStartHistory = pref.historyHeight();
        grabStartInputY = pref.inputY();
        grabTarget = target;
    }

    public static void finishGrab(int hand) {
        if (hand != grabHand || keyboard == null) return;
        grabHand = -1;
        grabStartController = null;
        keyboard.features().savePosition();
    }

    private static Vector3f controllerRoomPosition(int hand) {
        try {
            Class<?> holderType = Class.forName("org.vivecraft.client_vr.ClientDataHolderVR");
            Object holder = holderType.getMethod("getInstance").invoke(null);
            Object player = holderType.getField("vrPlayer").get(holder);
            Object room = player.getClass().getField("vrdata_room_pre").get(player);
            Object controller = room.getClass().getMethod("getController", int.class)
                .invoke(room, hand);
            Object vec = controller.getClass().getMethod("getPositionF").invoke(controller);
            return vec instanceof Vector3f v ? new Vector3f(v) : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            return null;
        }
    }

    private static void followGrab(Matrix4f guiRotation, float guiScale) {
        if (grabHand < 0 || grabStartController == null || keyboard == null) return;
        Vector3f current = controllerRoomPosition(grabHand);
        if (current == null || guiScale <= 0f) return;
        Vector3f delta = current.sub(grabStartController);
        Vector3f localRight = guiRotation.transformDirection(
            new Vector3f(-1, 0, 0), new Vector3f()).normalize();
        Vector3f localUp = guiRotation.transformDirection(
            new Vector3f(0, 1, 0), new Vector3f()).normalize();
        Vector3f localForward = guiRotation.transformDirection(
            new Vector3f(0, 0, 1), new Vector3f()).normalize();
        if ("FREE".equals(grabTarget)) {
            keyboard.features().setFreePosition(
                grabStartRight + delta.dot(localRight) / guiScale,
                grabStartDown - delta.dot(localUp) / guiScale,
                grabStartForward + delta.dot(localForward) / guiScale);
        } else keyboard.features().setPosition(
            grabStartRight + delta.dot(localRight) / guiScale,
            grabStartDown - delta.dot(localUp) / guiScale,
            grabStartForward + delta.dot(localForward) / guiScale);
        // Different targets do not move the keyboard pose. Edit only the
        // selected target while the right trigger is held.
        if ("HISTORY".equals(grabTarget)) {
            keyboard.features().setPosition(grabStartRight, grabStartDown, grabStartForward);
            keyboard.features().setHistoryHeight(
                grabStartHistory + delta.dot(localUp) / guiScale);
        } else if ("INPUT".equals(grabTarget)) {
            keyboard.features().setPosition(grabStartRight, grabStartDown, grabStartForward);
            keyboard.features().setInputY(grabStartInputY -
                Math.round(150f * delta.dot(localUp) / guiScale));
        }
    }
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
            finishGrab(grabHand);
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
            // While playing with FREE keyboard, GuiHandler may no longer
            // expose a GUI pose; keep the last valid chat-room anchor.
            boolean free = keyboard.isFreeMode();
            if ((!free || placementAnchor == null || placementRotation == null) &&
                (!(chatPos instanceof Vector3f) ||
                 !(chatRotation instanceof Matrix4f)))
                return;

            float guiScale = guiClass.getField("GUI_SCALE").getFloat(null);
            if (!(guiScale > 0)) return;
            // Pin the keyboard to its own room pose for this screen. Subsequent
            // chat window movement must not drag the keyboard along with it.
            if ((!free && placementScreen != client.currentScreen) ||
                placementAnchor == null || placementRotation == null) {
                if (!(chatPos instanceof Vector3f guiPos) ||
                    !(chatRotation instanceof Matrix4f guiRot)) return;
                placementScreen = client.currentScreen;
                placementAnchor = new Vector3f(guiPos);
                placementRotation = new Matrix4f(guiRot);
            }
            Matrix4f baseRotation = placementRotation;
            followGrab(baseRotation, guiScale);
            FoxCraftFeatureSettings settings = keyboard.features();
            // Chat history uses an isolated HUD transform. Never mutate
            // GuiHandler.GUI_POS_ROOM: doing so also moves the chat input
            // and breaks independent height controls or other GUI elements.
            boolean inChat = free || client.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen;
            // Equivalent to QuestCraft's chat-relative keyboard positioning:
            // project down the GUI's local up axis and slightly toward player.
            // Neither KeyboardHandler.SHOWING nor its framebuffer is modified.
            float down = inChat ? BELOW_CHAT : BELOW_MENU;
            float forward = inChat ? FORWARD_FROM_CHAT : MENU_FORWARD;
            rotation = new Matrix4f(baseRotation)
                .rotateX((float) Math.toRadians(settings.keyboardTilt()));
            float xCorrection = (inChat ? CHAT_RIGHT_SHIFT : MENU_RIGHT_SHIFT)
                + (free ? settings.freeRight() : settings.offsetRight());
            float yCorrection = (inChat ? CHAT_DOWN_SHIFT : MENU_DOWN_SHIFT)
                + (free ? settings.freeDown() : settings.offsetDown());
            position = new Vector3f(placementAnchor)
                .sub(baseRotation.transformDirection(new Vector3f(0, 1, 0),
                    new Vector3f()).mul((down + yCorrection) * guiScale))
                // +X in Vivecraft's GUI basis means LEFT, not RIGHT.
                // Apply the offset to the shared room-space position, never
                // only to the drawn quad: projected controller UVs follow it.
                .sub(baseRotation.transformDirection(new Vector3f(1, 0, 0),
                    new Vector3f()).mul(xCorrection * guiScale))
                .add(baseRotation.transformDirection(new Vector3f(0, 0, 1),
                    new Vector3f()).mul(
                        (forward + (free ? settings.freeForward() : settings.offsetForward())) * guiScale))
                .add(rotation.transformDirection(new Vector3f(0, 0, 1),
                    new Vector3f()).mul(TOWARDS_PLAYER * guiScale));
            scale = Math.max(0.25f, guiScale *
                (inChat ? SCALE_MULTIPLIER : MENU_SCALE_MULTIPLIER) *
                settings.keyboardSize() / 100f);
            int w = guiClass.getField("GUI_WIDTH").getInt(null);
            int h = guiClass.getField("GUI_HEIGHT").getInt(null);
            int sw = guiClass.getField("SCALED_WIDTH_MAX").getInt(null);
            int sh = guiClass.getField("SCALED_HEIGHT_MAX").getInt(null);
            if (w <= 0 || h <= 0 || sw < 220 || sh < 140) return;
            // Preserve EXACTLY the same logical UI coordinate system as
            // GuiHandler.getTexCoordsForCursor. Supersampling the offscreen
            // framebuffer without also scaling RenderHelper's projection
            // shrinks/translates the painted keys while hitboxes remain put.
            int sampleFactor = Math.max(w, h) * HD_RENDER_SCALE <= MAX_HD_TARGET_SIDE
                ? HD_RENDER_SCALE : 1;
            int hdWidth = w * sampleFactor;
            int hdHeight = h * sampleFactor;
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
            // RenderHelper builds its orthographic matrix from
            // target.width / GUI_SCALE_FACTOR_MAX. Scale BOTH numerator and
            // denominator so the painted panel stays registered to the
            // untouched input UVs (u*uiWidth, v*uiHeight).
            Field guiMaxScale = guiClass.getField("GUI_SCALE_FACTOR_MAX");
            int originalMaxScale = guiMaxScale.getInt(null);
            if (originalMaxScale <= 0) throw new IllegalStateException("Invalid GUI scale");
            try {
                guiMaxScale.setInt(null, Math.multiplyExact(originalMaxScale, sampleFactor));
                render.invoke(null, draw, client.getRenderTickCounter(), screen, true);
                ready = true;
            } finally {
                guiMaxScale.setInt(null, originalMaxScale);
            }
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
        // GUI actions remain active while QuestCraft's own keyboard is closed.
        // Read them; NEVER alter controller bindings or Minecraft controls.
        Object guiLeftClick = FoxCraftGuiClickInput.optionalBinding(guiClass, "KEY_LEFT_CLICK");
        Object guiRightClick = FoxCraftGuiClickInput.optionalBinding(guiClass, "KEY_RIGHT_CLICK");
        Method cursorProject = null;
        for (Method method : guiClass.getMethods()) {
            if (method.getName().equals("getTexCoordsForCursor") &&
                method.getParameterCount() == 4) cursorProject = method;
        }
        if (cursorProject == null)
            throw new NoSuchMethodException("GuiHandler.getTexCoordsForCursor");
        for (int hand = 0; hand < 2; hand++) {
            FoxCraftFeatureSettings pref = keyboard.features();
            boolean allowed = hand == 0 ? pref.rightHand() : pref.leftHand();
            if (!allowed) {
                if (wasPressed[hand]) keyboard.vrReleased(original,
                    cursorX[hand], cursorY[hand], hand);
                wasPressed[hand] = false;
                cursorX[hand] = -100;
                cursorY[hand] = -100;
                continue;
            }
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
            // The stock keyboard action may be inactive after QuestCraft
            // closes its own keyboard. Accept ordinary GUI click ONLY when
            // its OpenXR origin identifies THIS physical controller.
            boolean down = tracked && FoxCraftGuiClickInput.pressed(
                provider, handed, keyboardClick, guiLeftClick, guiRightClick,
                FoxCraftGuiClickInput.optionalLeftUseBinding());
            boolean accepted = down;
            if (down && !wasPressed[hand]) {
                accepted = x >= 0 && y >= 0 &&
                    keyboard.vrPressed(original, x, y, hand);
                if (accepted) {
                    // No text/chat data. Confirms which physical ray actually
                    // accepted the press when diagnosing real Quest hardware.
                    System.out.println("[FoxCraft][QXR-hand-v1] " +
                        "key press: controller=" + handed + " rayIndex=" + hand);
                }
            } else if (!down && wasPressed[hand]) {
                keyboard.vrReleased(original, x, y, hand);
            } else if (down) {
                keyboard.vrMoved(original, x, y, hand);
            }
            // Do not latch a rejected click: if the detached VR plane
            // appears one frame after the controller press, retry.
            wasPressed[hand] = accepted;
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
        placementScreen = null;
        placementAnchor = null;
        placementRotation = null;
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

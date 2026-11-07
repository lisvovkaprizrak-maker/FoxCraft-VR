package dev.foxcraft.keyboard;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * VR chat group handles. Both are on the normal chat GUI plane, not on the
 * independent keyboard. Left moves the full chat in-plane; right also changes
 * depth (closer/farther). Keeping the GUI plane together moves chat text,
 * history background, input letters and input background as one room panel.
 */
public final class FoxCraftChatGrab {
    private static Screen owner;
    private static Vector3f baseRoomPose;
    private static Vector3f appliedRoomPose;
    private static Object appliedRoomObject;
    private static int grab = 0; // 1 = XY, 2 = XYZ
    private static Vector3f startController;
    private static float initialRight, initialDown, initialForward;
    private static int activeWidth, activeHeight;
    private static boolean moving;

    private FoxCraftChatGrab() {}

    public static void render(Screen screen, DrawContext draw) {
        if (!(screen instanceof ChatScreen) || !FoxCraftVrPlane.isVrSession()) return;
        activeWidth = screen.width;
        activeHeight = screen.height;
        drawHandle(draw, 9, Math.max(8, screen.height - 215), "✥",
            grab == 1);
        drawHandle(draw, Math.max(9, screen.width - 35),
            Math.max(8, screen.height - 67), "↔", grab == 2);
    }

    private static void drawHandle(DrawContext draw, int x, int y,
                                   String label, boolean active) {
        draw.fill(x-2,y-2,x+23,y+19,active?0xFFFF8B16:0xFF9CA7B7);
        draw.fill(x,y,x+21,y+17,0xEE252932);
        draw.drawCenteredTextWithShadow(
            MinecraftClient.getInstance().textRenderer, label,
            x+10,y+5,0xFFE8EDF4);
    }

    public static boolean mousePressed(Screen screen, double x, double y, int button) {
        if (!(screen instanceof ChatScreen) || button != 0 ||
            !FoxCraftVrPlane.isVrSession()) return false;
        int kind = inHandle(x,y,9,Math.max(8,screen.height-215)) ? 1 :
            inHandle(x,y,Math.max(9,screen.width-35),
                Math.max(8,screen.height-67)) ? 2 : 0;
        if (kind == 0) return false;
        Vector3f controller = FoxCraftVrPlane.controllerRoomPosition(0);
        if (controller == null) return false;
        FoxCraftFeatureSettings settings = FoxCraftVrPlane.features();
        if (settings == null) return false;
        grab = kind;
        moving = true;
        startController = controller;
        initialRight = settings.chatRight();
        initialDown = settings.chatDown();
        initialForward = settings.chatForward();
        return true;
    }

    public static boolean mouseReleased(Screen screen, double x, double y, int button) {
        if (button != 0 || !moving) return false;
        finish();
        return true;
    }

    private static boolean inHandle(double x, double y, int bx, int by) {
        return x >= bx-2 && x < bx+23 && y >= by-2 && y < by+19;
    }

    /**
     * Called at the beginning of a VR draw. Vivecraft may reposition its GUI
     * each frame, so remember its untreated pose and apply only OUR offsets.
     * Never accumulate movement on the already offset position.
     */
    public static void updateRoomPose(Screen screen, Vector3f currentGuiPos,
                                      Matrix4f guiRotation, float guiScale) {
        if (!(screen instanceof ChatScreen) ||
            currentGuiPos == null || guiRotation == null || guiScale <= 0) {
            if (moving) finish();
            if (appliedRoomObject instanceof Vector3f old &&
                appliedRoomPose != null && baseRoomPose != null &&
                old.distanceSquared(appliedRoomPose) < 0.00001f)
                old.set(baseRoomPose);
            owner = null;
            baseRoomPose = appliedRoomPose = null;
            appliedRoomObject = null;
            return;
        }
        if (owner != screen || baseRoomPose == null ||
            appliedRoomObject != currentGuiPos ||
            (appliedRoomPose != null &&
                currentGuiPos.distanceSquared(appliedRoomPose) > 0.00001f)) {
            owner = screen;
            baseRoomPose = new Vector3f(currentGuiPos);
        }
        FoxCraftFeatureSettings settings = FoxCraftVrPlane.features();
        if (settings == null) return;
        if (moving && startController != null) {
            Vector3f controller = FoxCraftVrPlane.controllerRoomPosition(0);
            if (controller != null) {
                Vector3f delta = controller.sub(startController);
                Vector3f right = guiRotation.transformDirection(
                    new Vector3f(-1,0,0),new Vector3f()).normalize();
                Vector3f up = guiRotation.transformDirection(
                    new Vector3f(0,1,0),new Vector3f()).normalize();
                Vector3f forward = guiRotation.transformDirection(
                    new Vector3f(0,0,1),new Vector3f()).normalize();
                settings.setChatRoomPosition(
                    initialRight + delta.dot(right)/guiScale,
                    initialDown - delta.dot(up)/guiScale,
                    grab == 2
                        ? initialForward + delta.dot(forward)/guiScale
                        : initialForward);
            }
        }
        Vector3f newPose = new Vector3f(baseRoomPose)
            .add(guiRotation.transformDirection(
                new Vector3f(-1,0,0),new Vector3f())
                .mul(settings.chatRight()*guiScale))
            .sub(guiRotation.transformDirection(
                new Vector3f(0,1,0),new Vector3f())
                .mul(settings.chatDown()*guiScale))
            .add(guiRotation.transformDirection(
                new Vector3f(0,0,1),new Vector3f())
                .mul(settings.chatForward()*guiScale));
        currentGuiPos.set(newPose);
        appliedRoomObject = currentGuiPos;
        appliedRoomPose = newPose;
    }

    public static void finish() {
        if (!moving) return;
        moving = false;
        grab = 0;
        startController = null;
        FoxCraftFeatureSettings settings = FoxCraftVrPlane.features();
        if (settings != null) settings.savePosition();
    }
}

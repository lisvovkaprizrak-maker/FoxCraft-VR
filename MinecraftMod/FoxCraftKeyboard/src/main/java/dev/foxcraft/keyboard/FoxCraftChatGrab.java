package dev.foxcraft.keyboard;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Room-space transform for the entire chat GUI. The sole chat XYZ grab
 * handle is now drawn on FoxCraft's keyboard (top-left), never on chat.
 * Vivecraft renders text, chat history/backdrop and text input/backdrop on
 * the same GUI plane, so an XYZ translation moves all of them together.
 */
public final class FoxCraftChatGrab {
    private static Screen owner;
    private static Vector3f baseRoomPose;
    private static Vector3f appliedRoomPose;
    private static Object appliedRoomObject;
    private static Vector3f startController;
    private static float initialRight, initialDown, initialForward;
    private static boolean moving;

    private FoxCraftChatGrab() {}

    /**
     * Start a full XYZ chat grab from the TOP-LEFT KEYBOARD key.
     * A grab is valid only with the chat open, using the right controller.
     * Keyboard movement is controlled separately by FoxCraftVrPlane.
     */
    public static boolean beginFromKeyboard(int hand) {
        if (hand != 0 ||
            !(MinecraftClient.getInstance().currentScreen instanceof ChatScreen) ||
            moving) return false;
        Vector3f controller = FoxCraftVrPlane.controllerRoomPosition(hand);
        FoxCraftFeatureSettings settings = FoxCraftVrPlane.features();
        if (controller == null || settings == null) return false;
        startController = controller;
        initialRight = settings.chatRight();
        initialDown = settings.chatDown();
        initialForward = settings.chatForward();
        moving = true;
        return true;
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
                    initialForward + delta.dot(forward)/guiScale);
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
        startController = null;
        FoxCraftFeatureSettings settings = FoxCraftVrPlane.features();
        if (settings != null) settings.savePosition();
    }
}

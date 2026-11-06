package dev.foxcraft.keyboard;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import org.joml.Matrix4f;

import java.lang.reflect.Method;

/**
 * Optional Quest-style white pointer beams while FoxCraft is open.
 * Draws using Vivecraft's own controller view transform, without changing
 * input bindings or the vanilla game cursor. Fail closed on incompatible forks.
 */
public final class FoxCraftControllerRays {
    // Short cosmetic pointers, not the actual interaction / hit-test ray.
    private static final float BEAM_START_Z = -0.04f;
    private static final float BEAM_END_Z = -0.38f;
    private static final float BEAM_HALF_WIDTH = 0.0035f;

    private static Method setup;
    private static Method tracking;
    private static Object vr;
    private static boolean unavailable;
    private static boolean warned;

    private FoxCraftControllerRays() {}

    public static void render(boolean renderRight, boolean renderLeft) {
        if (unavailable || !FoxCraftVrPlane.isKeyboardActive()) return;
        if (!renderRight && !renderLeft) return;
        try {
            if (setup == null) {
                Class<?> helper = Class.forName(
                    "org.vivecraft.client_vr.render.helpers.RenderHelper");
                setup = helper.getMethod("setupRenderingAtController",
                    int.class, Matrix4f.class);
                Class<?> holderClass = Class.forName("org.vivecraft.client_vr.ClientDataHolderVR");
                Object holder = holderClass.getMethod("getInstance").invoke(null);
                vr = holderClass.getField("vr").get(holder);
                tracking = vr.getClass().getMethod("isControllerTracking", int.class);
            }
            MinecraftClient client = MinecraftClient.getInstance();
            VertexConsumerProvider.Immediate buffers =
                client.getBufferBuilders().getEntityVertexConsumers();
            VertexConsumer consumer = buffers.getBuffer(RenderLayer.getLines());
            for (int hand = 0; hand < 2; hand++) {
                if ((hand == 0 && !renderRight) ||
                    (hand == 1 && !renderLeft)) continue;
                if (!(Boolean) tracking.invoke(vr, hand)) continue;
                Matrix4f matrix = new Matrix4f();
                setup.invoke(null, hand, matrix);
                // Keep Quest's tested forward (-Z) direction. The visual ray
                // stops before the keyboard; controller hit-testing is unchanged.
                drawTaperedPointer(consumer, matrix);
            }
            buffers.draw(RenderLayer.getLines());
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            unavailable = true;
            if (!warned) {
                warned = true;
                System.err.println("[FoxCraft] VR pointer beams unavailable: " +
                    error.getClass().getSimpleName());
            }
        }
    }

    /** Toothpick silhouette: narrow at controller and tip, thickest midway. */
    private static void drawTaperedPointer(VertexConsumer consumer, Matrix4f matrix) {
        float middleZ = (BEAM_START_Z + BEAM_END_Z) * 0.5f;
        // Four slim longitudinal ridges make a symmetric double-ended taper.
        for (int axis = 0; axis < 4; axis++) {
            float dx = axis == 0 ? BEAM_HALF_WIDTH :
                       axis == 1 ? -BEAM_HALF_WIDTH : 0f;
            float dy = axis == 2 ? BEAM_HALF_WIDTH :
                       axis == 3 ? -BEAM_HALF_WIDTH : 0f;
            addLine(consumer, matrix, 0f, 0f, BEAM_START_Z,
                dx, dy, middleZ, 155, 220);
            addLine(consumer, matrix, dx, dy, middleZ,
                0f, 0f, BEAM_END_Z, 220, 110);
        }
    }

    private static void addLine(VertexConsumer consumer, Matrix4f matrix,
                                float ax, float ay, float az,
                                float bx, float by, float bz,
                                int startAlpha, int endAlpha) {
        consumer.vertex(matrix, ax, ay, az)
            .color(245, 248, 255, startAlpha).normal(0, 1, 0);
        consumer.vertex(matrix, bx, by, bz)
            .color(245, 248, 255, endAlpha).normal(0, 1, 0);
    }
}

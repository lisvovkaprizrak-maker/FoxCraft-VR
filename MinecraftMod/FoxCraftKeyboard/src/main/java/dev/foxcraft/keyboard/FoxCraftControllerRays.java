package dev.foxcraft.keyboard;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import org.joml.Matrix4f;

import java.lang.reflect.Method;

/**
 * Optional thin visible pointer beams for both Quest controllers.
 * Draws using Vivecraft's own controller view transform, without changing
 * input bindings or the vanilla game cursor. Fail closed on incompatible forks.
 */
public final class FoxCraftControllerRays {
    private static Method setup;
    private static Method tracking;
    private static Object vr;
    private static boolean unavailable;
    private static boolean warned;

    private FoxCraftControllerRays() {}

    public static void render(boolean renderRight, boolean renderLeft) {
        if (unavailable || !FoxCraftVrPlane.isVrSession()) return;
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
                // Both rays originate at the controllers' aim axis. A faint
                // orange right beam and cool-blue left beam distinguish hands.
                int red = hand == 0 ? 255 : 92;
                int green = hand == 0 ? 156 : 180;
                int blue = hand == 0 ? 32 : 255;
                addLine(consumer, matrix, 0f, 0f, -0.04f,
                    0f, 0f, -1.8f, red, green, blue);
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

    private static void addLine(VertexConsumer consumer, Matrix4f matrix,
                                float ax, float ay, float az,
                                float bx, float by, float bz,
                                int r, int g, int b) {
        consumer.vertex(matrix, ax, ay, az)
            .color(r, g, b, 220).normal(0, 1, 0);
        consumer.vertex(matrix, bx, by, bz)
            .color(r, g, b, 160).normal(0, 1, 0);
    }
}

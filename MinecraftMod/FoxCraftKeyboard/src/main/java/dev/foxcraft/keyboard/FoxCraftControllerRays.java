package dev.foxcraft.keyboard;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.lang.reflect.Method;

/**
 * Optional Quest-style white pointer beams while FoxCraft is open.
 * Draws using Vivecraft's own controller view transform, without changing
 * input bindings or the vanilla game cursor. Fail closed on incompatible forks.
 */
public final class FoxCraftControllerRays {
    // Short cosmetic pointers, not the actual interaction / hit-test ray.
    private static final float START_OFFSET = 0.04f;
    private static final float DEFAULT_BEAM_LENGTH = 0.38f;
    private static final float GAP_BEFORE_KEYBOARD = 0.06f;
    // A thin pale-white Quest pointer with a subtle blue fringe.
    private static final float BEAM_HALF_WIDTH = 0.0018f;

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
                FoxCraftFeatureSettings prefs = FoxCraftVrPlane.features();
                if (prefs != null &&
                    !(hand == 0 ? prefs.rightHand() : prefs.leftHand())) continue;
                // Vivecraft's panel projection is the authoritative click
                // target. Aim our SHORT cosmetic beam exactly along that same
                // line, not along a guessed fixed -Z controller axis.
                Vector3f eyeRelativeHit = FoxCraftVrPlane.controllerHitRelativeToEye(hand);
                Matrix4f matrix = new Matrix4f();
                setup.invoke(null, hand, matrix);
                // Never hide the laser merely because its ray currently misses
                // the keyboard. The player needs the straight visible guide to
                // find the panel. Prefer the exact hit direction when available.
                Vector3f localDirection = new Vector3f(0f, 0f, -1f);
                if (eyeRelativeHit != null) {
                    Vector3f hit = new Matrix4f(matrix).invert()
                        .transformPosition(eyeRelativeHit, new Vector3f());
                    if (hit.isFinite() && hit.lengthSquared() > 0.01f)
                        localDirection.set(hit);
                }
                float beamMetres = prefs != null ?
                    prefs.beamCentimetres() / 100f : DEFAULT_BEAM_LENGTH;
                drawTaperedPointer(consumer, matrix, localDirection, beamMetres);
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

    /**
     * Real collision point defines direction, but the rendered beam stops
     * short of the keyboard. Same geometry for either controller and any VR
     * panel orientation; longitudinal ridges taper toward both ends.
     */
    private static void drawTaperedPointer(VertexConsumer consumer,
                                           Matrix4f matrix, Vector3f localTarget,
                                           float maxBeamLength) {
        float distance = localTarget.length();
        if (!(distance > 0.001f) || !localTarget.isFinite()) return;
        Vector3f direction = localTarget.normalize(new Vector3f());
        // The visible pointer is intentionally short, even if the click ray
        // intersects the panel farther away. When no hit is available, still
        // show the normal straight pointer for calibration.
        float end = Math.min(maxBeamLength,
            Math.max(START_OFFSET + 0.05f, distance - GAP_BEFORE_KEYBOARD));
        Vector3f start = new Vector3f(direction).mul(START_OFFSET);
        Vector3f finish = new Vector3f(direction).mul(end);
        Vector3f midpoint = new Vector3f(start).add(finish).mul(0.5f);

        // Pick a stable pair of vectors perpendicular to the true aim.
        Vector3f horizontal = new Vector3f(0f, 1f, 0f).cross(direction);
        if (horizontal.lengthSquared() < 0.01f)
            horizontal.set(1f, 0f, 0f);
        horizontal.normalize().mul(BEAM_HALF_WIDTH);
        Vector3f vertical = new Vector3f(direction).cross(horizontal)
            .normalize().mul(BEAM_HALF_WIDTH);
        Vector3f[] sides = {
            horizontal, new Vector3f(horizontal).negate(),
            vertical, new Vector3f(vertical).negate()
        };
        for (Vector3f offset : sides) {
            Vector3f middleSide = new Vector3f(midpoint).add(offset);
            addLine(consumer, matrix, start.x, start.y, start.z,
                middleSide.x, middleSide.y, middleSide.z, 115, 205);
            addLine(consumer, matrix, middleSide.x, middleSide.y, middleSide.z,
                finish.x, finish.y, finish.z, 205, 70);
        }
    }

    private static void addLine(VertexConsumer consumer, Matrix4f matrix,
                                float ax, float ay, float az,
                                float bx, float by, float bz,
                                int startAlpha, int endAlpha) {
        consumer.vertex(matrix, ax, ay, az)
            .color(232, 245, 255, startAlpha).normal(0, 1, 0);
        consumer.vertex(matrix, bx, by, bz)
            .color(232, 245, 255, endAlpha).normal(0, 1, 0);
    }
}

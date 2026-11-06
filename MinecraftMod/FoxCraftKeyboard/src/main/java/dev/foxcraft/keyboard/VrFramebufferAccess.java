package dev.foxcraft.keyboard;

import net.minecraft.client.gl.Framebuffer;

/** Implemented on MinecraftClient by FoxCraft's own, targeted accessor mixin. */
public interface VrFramebufferAccess {
    void foxcraft$setRenderTarget(Framebuffer target);
}

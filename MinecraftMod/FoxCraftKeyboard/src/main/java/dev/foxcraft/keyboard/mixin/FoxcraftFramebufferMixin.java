package dev.foxcraft.keyboard.mixin;

import dev.foxcraft.keyboard.VrFramebufferAccess;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

/** Only while drawing a dedicated VR surface; original target is restored. */
@Mixin(MinecraftClient.class)
public abstract class FoxcraftFramebufferMixin implements VrFramebufferAccess {
    @Shadow @Final @Mutable private Framebuffer framebuffer;

    @Override
    public void foxcraft$setRenderTarget(Framebuffer newTarget) {
        this.framebuffer = newTarget;
    }
}

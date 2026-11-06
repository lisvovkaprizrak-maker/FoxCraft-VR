package dev.foxcraft.keyboard.mixin;

import dev.foxcraft.keyboard.FoxCraftVrPlane;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional Vivecraft hook: fill FoxCraft's independent framebuffer before VR passes. */
@Pseudo
@Mixin(targets = "org.vivecraft.client_vr.render.helpers.VRPassHelper", remap = false)
public abstract class VRPassHelperMixin {
    @Inject(method = "renderAndSubmit", at = @At("HEAD"), require = 0, remap = false)
    private static void foxcraft$prepare(CallbackInfo ci) {
        FoxCraftVrPlane.prepareFrame();
    }
}

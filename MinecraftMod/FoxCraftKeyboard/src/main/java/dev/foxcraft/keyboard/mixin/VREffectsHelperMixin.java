package dev.foxcraft.keyboard.mixin;

import dev.foxcraft.keyboard.FoxCraftVrPlane;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Render FoxCraft below Minecraft chat as a separate world-space quad. */
@Pseudo
@Mixin(targets = "org.vivecraft.client_vr.render.helpers.VREffectsHelper", remap = false)
public abstract class VREffectsHelperMixin {
    @Inject(method = "renderGuiAndShadow", at = @At("TAIL"), require = 0, remap = false)
    private static void foxcraft$renderPlane(
            float partialTick, boolean depthAlways, boolean shadowFirst,
            CallbackInfo ci) {
        FoxCraftVrPlane.renderWorld(partialTick, depthAlways);
    }
}

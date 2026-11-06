package dev.foxcraft.keyboard.mixin;

import dev.foxcraft.keyboard.FoxCraftControllerRays;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Display pointer beams along both tracked controller aim axes. */
@Pseudo
@Mixin(targets = "org.vivecraft.client_vr.render.helpers.VRArmHelper", remap = false)
public abstract class VRArmHelperMixin {
    @Inject(method = "renderVRHands", at = @At("TAIL"), require = 0, remap = false)
    private static void foxcraft$renderPointerRays(float partialTick,
        boolean renderMain, boolean renderOff,
        boolean menuHandMain, boolean menuHandOff, CallbackInfo ci) {
        FoxCraftControllerRays.render(renderMain, renderOff);
    }
}

package dev.foxcraft.keyboard.mixin;

import dev.foxcraft.keyboard.FoxCraftVrPlane;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While a VR ray touches the FREE keyboard, pressing its physical trigger
 * must not also break/place a block behind the keyboard. Away from the
 * keyboard, Minecraft input is left intact (running/jumping included).
 */
@Mixin(MinecraftClient.class)
public abstract class FreeKeyboardGameplayGuardMixin {
    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true, require = 0)
    private void foxcraft$preventTypingAttack(CallbackInfoReturnable<Boolean> cir) {
        if (FoxCraftVrPlane.interceptsGameplayControls()) cir.setReturnValue(false);
    }

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true, require = 0)
    private void foxcraft$preventTypingItemUse(CallbackInfo ci) {
        if (FoxCraftVrPlane.interceptsGameplayControls()) ci.cancel();
    }
}

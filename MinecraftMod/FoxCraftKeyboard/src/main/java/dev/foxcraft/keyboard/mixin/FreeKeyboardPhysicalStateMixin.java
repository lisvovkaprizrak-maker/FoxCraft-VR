package dev.foxcraft.keyboard.mixin;

import dev.foxcraft.keyboard.FreeKeyboardGameInput;
import net.minecraft.client.util.InputUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Synthetic held F3/Ctrl/Shift must also be readable to legacy mods that
 * poll InputUtil.isKeyPressed instead of receiving Keyboard.onKey events.
 * Returns true only while a FoxCraft free-mode key is physically held or
 * its sticky modifier is enabled. Physical key state otherwise stays native.
 */
@Mixin(InputUtil.class)
public abstract class FreeKeyboardPhysicalStateMixin {
    @Inject(method = "isKeyPressed(JI)Z", at = @At("HEAD"),
        cancellable = true, require = 0)
    private static void foxcraft$virtualHeld(long window, int key,
        CallbackInfoReturnable<Boolean> cir) {
        if (FreeKeyboardGameInput.isSyntheticPressed(key))
            cir.setReturnValue(true);
    }
}

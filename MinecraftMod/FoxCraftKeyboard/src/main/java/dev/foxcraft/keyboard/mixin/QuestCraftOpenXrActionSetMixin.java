package dev.foxcraft.keyboard.mixin;

import dev.foxcraft.keyboard.FoxCraftVrPlane;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * QuestCraft OpenXR only activates KEYBOARD actions if its own keyboard overlay
 * is SHOWING. FoxCraft hides that overlay, so both index trigger bindings
 * otherwise go inactive. Keep just the existing KEYBOARD input action set
 * enabled while the FoxCraft text keyboard is active. This does not show the
 * stock keyboard, remap controllers, or affect input outside text entry.
 *
 * Optional: upstream Vivecraft and other providers are untouched.
 */
@Pseudo
@Mixin(targets = "org.vivecraft.client_vr.provider.openxr.MCOpenXR", remap = false)
public abstract class QuestCraftOpenXrActionSetMixin {
    @Redirect(
        method = "updateActiveActionSets",
        at = @At(value = "FIELD",
            target = "Lorg/vivecraft/client_vr/gameplay/screenhandlers/KeyboardHandler;SHOWING:Z",
            opcode = 178,
            remap = false),
        require = 0,
        remap = false
    )
    private boolean foxcraft$keepKeyboardActionSet() {
        boolean nativeShowing = false;
        try {
            Class<?> handler = Class.forName(
                "org.vivecraft.client_vr.gameplay.screenhandlers.KeyboardHandler");
            nativeShowing = handler.getField("SHOWING").getBoolean(null);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Optional QuestCraft fork: only use FoxCraft's current state.
        }
        return nativeShowing || FoxCraftVrPlane.isKeyboardActive();
    }
}

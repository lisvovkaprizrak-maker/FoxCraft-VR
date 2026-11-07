package dev.foxcraft.keyboard.mixin;

import dev.foxcraft.keyboard.FoxCraftFeatureSettings;
import dev.foxcraft.keyboard.FoxCraftVrPlane;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * The vanilla chat input's dark rectangle is drawn at screen-bottom
 * coordinates, even when FoxCraft has moved chatField higher or lower.
 * Move that rectangle to the actual widget bounds; this also keeps it
 * synchronized with the glyphs on the first frame after a position change.
 */
@Mixin(ChatScreen.class)
public abstract class FoxCraftChatInputBackgroundMixin {
    @Shadow protected TextFieldWidget chatField;

    @ModifyArgs(method = "render", require = 0,
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V"))
    private void foxcraft$moveInputBackgroundWithText(Args args) {
        if (!FoxCraftVrPlane.isVrSession() || chatField == null) return;
        FoxCraftFeatureSettings settings = FoxCraftVrPlane.features();
        if (settings == null || settings.inputY() == 0) return;
        // Distinguish the narrow input bar from other DrawContext.fill calls.
        int top = (Integer) args.get(1);
        int bottom = (Integer) args.get(3);
        int expectedVanillaBottom =
            chatField.getY() - settings.inputY() + chatField.getHeight();
        if (bottom <= top || bottom - top > 24 ||
            Math.abs(bottom - expectedVanillaBottom) > 16) return;
        int left = chatField.getX();
        int right = left + chatField.getWidth();
        int fieldTop = chatField.getY();
        int fieldBottom = fieldTop + chatField.getHeight();
        // Use a shared origin for the text field and its background.
        args.set(0, left - 2);
        args.set(1, fieldTop - 2);
        args.set(2, right + 2);
        args.set(3, fieldBottom + 2);
    }
}

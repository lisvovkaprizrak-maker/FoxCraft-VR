package dev.foxcraft.keyboard.mixin;

import dev.foxcraft.keyboard.FoxCraftFeatureSettings;
import dev.foxcraft.keyboard.FoxCraftVrPlane;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Moves only the chat history, never the chat input field or keyboard.
 * Paired matrix push/pop keeps other HUD elements and Vivecraft ray math intact.
 */
@Mixin(ChatHud.class)
public abstract class FoxCraftChatHudMixin {
    @Unique private boolean foxcraft$transformed;

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void foxcraft$beforeChatHistory(DrawContext context, int currentTick,
                                            int mouseX, int mouseY,
                                            boolean focused, CallbackInfo ci) {
        foxcraft$transformed = false;
        if (!FoxCraftVrPlane.isVrSession()) return;
        FoxCraftFeatureSettings prefs = FoxCraftVrPlane.features();
        if (prefs == null || (prefs.historyHeight() == 0f &&
                             prefs.chatTextSize() == 100)) return;
        float scale = prefs.chatTextSize() / 100f;
        // Anchor scaling at the bottom edge so the chat history stays visible.
        float bottom = context.getScaledWindowHeight();
        context.getMatrices().push();
        context.getMatrices().translate(0f,
            bottom * (1f - scale) - prefs.historyHeight() * 150f, 0f);
        context.getMatrices().scale(scale, scale, 1f);
        foxcraft$transformed = true;
    }

    @Inject(method = "render", at = @At("RETURN"), require = 0)
    private void foxcraft$afterChatHistory(DrawContext context, int currentTick,
                                           int mouseX, int mouseY,
                                           boolean focused, CallbackInfo ci) {
        if (foxcraft$transformed) {
            context.getMatrices().pop();
            foxcraft$transformed = false;
        }
    }
}

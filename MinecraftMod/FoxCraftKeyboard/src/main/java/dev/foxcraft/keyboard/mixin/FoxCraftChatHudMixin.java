package dev.foxcraft.keyboard.mixin;

import dev.foxcraft.keyboard.FoxCraftFeatureSettings;
import dev.foxcraft.keyboard.FoxCraftVrPlane;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Moves the entire chat-history area: text, its translucent backdrop and
 * vanilla message decorations. The editable input remains an independent
 * widget. Matching push/pop leaves the other HUD and VR pointer untouched.
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
        if (prefs == null) return;
        // Always render the chat history as a complete panel while its
        // input screen is open, even before any new message has been typed.
        // Previously only floating glyphs appeared without a matching
        // backdrop; the blank chat background had no visible placement.
        boolean chatOpen = MinecraftClient.getInstance().currentScreen instanceof ChatScreen;
        float scale = prefs.chatTextSize() / 100f;
        float bottom = context.getScaledWindowHeight();
        if (!chatOpen && prefs.historyHeight() == 0f &&
            prefs.chatTextSize() == 100) return;
        context.getMatrices().push();
        context.getMatrices().translate(0f,
            bottom * (1f - scale) - prefs.historyHeight() * 220f, 0f);
        context.getMatrices().scale(scale, scale, 1f);
        foxcraft$transformed = true;
        if (chatOpen) {
            // Background and entire history share EXACTLY the same matrix;
            // the vanilla text field is not part of this draw call.
            int right = Math.min(context.getScaledWindowWidth() - 6, 340);
            int top = Math.max(4, (int) bottom - 190);
            int base = (int) bottom - 33;
            if (right > 24 && base - top > 24) {
                context.fill(3, top, right, base, 0xA01A2029);
                context.fill(3, top, right, top + 1, 0xB4B6C1CB);
                context.fill(3, top, 4, base, 0x668E9BA9);
                context.fill(right - 1, top, right, base, 0x668E9BA9);
                context.fill(3, base - 1, right, base, 0x778E9BA9);
            }
        }
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

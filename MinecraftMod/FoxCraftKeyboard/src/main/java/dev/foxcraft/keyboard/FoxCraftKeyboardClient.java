package dev.foxcraft.keyboard;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;

/**
 * The Vivecraft renderer can display the Minecraft Screen itself in VR, so
 * keyboard keys are drawn inside that Screen rather than in Unity's launcher.
 * No injection into Android or Pojlib is needed for this route.
 */
public final class FoxCraftKeyboardClient implements ClientModInitializer {
    private final VirtualKeyboardOverlay keyboard = new VirtualKeyboardOverlay();

    @Override
    public void onInitializeClient() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            ScreenEvents.afterRender(screen).register((s, context, mouseX, mouseY, tickDelta) ->
                keyboard.render(s, context, mouseX, mouseY));

            ScreenMouseEvents.allowMouseClick(screen).register((s, mouseX, mouseY, button) ->
                !keyboard.handleClick(s, mouseX, mouseY, button));
        });
    }
}

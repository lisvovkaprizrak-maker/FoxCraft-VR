package dev.foxcraft.keyboard;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Vivecraft renderer can display the Minecraft Screen itself in VR, so
 * keyboard keys are drawn inside that Screen rather than in Unity's launcher.
 * No injection into Android or Pojlib is needed for this route.
 */
public final class FoxCraftKeyboardClient implements ClientModInitializer {
    // Do not log chat content, account details, typed keys or server addresses.
    private static final Logger LOGGER = LoggerFactory.getLogger("FoxCraftKeyboard");
    private final VirtualKeyboardOverlay keyboard = new VirtualKeyboardOverlay();

    @Override
    public void onInitializeClient() {
        FoxCraftVrPlane.attach(keyboard);
        LOGGER.info("[FoxCraft][QXR-hand-v1 2026-11-07] Minecraft 1.21.5 VR text overlay: physical OpenXR hand sampler enabled.");
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            ScreenEvents.afterRender(screen).register((s, context, mouseX, mouseY, tickDelta) ->
                keyboard.render(s, context, mouseX, mouseY));

            ScreenMouseEvents.allowMouseClick(screen).register((s, mouseX, mouseY, button) ->
                !keyboard.mousePressed(s, mouseX, mouseY, button));

            ScreenMouseEvents.allowMouseRelease(screen).register((s, mouseX, mouseY, button) ->
                !keyboard.mouseReleased(s, mouseX, mouseY, button));
        });
    }
}

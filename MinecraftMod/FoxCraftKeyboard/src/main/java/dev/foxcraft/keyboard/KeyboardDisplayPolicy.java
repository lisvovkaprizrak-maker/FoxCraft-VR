package dev.foxcraft.keyboard;

/**
 * FoxCraft is a chat keyboard, not a blanket overlay for every editable
 * Minecraft screen. World selection contains a search TextFieldWidget, but
 * rendering keyboard keys over the world list obscures vital menu options.
 */
public final class KeyboardDisplayPolicy {
    private KeyboardDisplayPolicy() {}

    public static boolean showScreenOverlay(boolean vrSession,
                                            boolean chatScreen,
                                            boolean roomPanelReady) {
        // Hide on world-selection, settings and all other menus regardless of
        // whether Vivecraft temporarily reports VR_RUNNING=false there.
        // Chat alone may show a fallback while the separate VR panel boots.
        return chatScreen && !roomPanelReady;
    }
}

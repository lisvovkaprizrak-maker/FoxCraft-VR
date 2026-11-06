package dev.foxcraft.keyboard;

/**
 * Keeps a usable keyboard in QuestCraft when optional room-space VR hooks fail.
 * Never draws FoxCraft directly over non-chat menus during a VR session.
 */
public final class KeyboardDisplayPolicy {
    private KeyboardDisplayPolicy() {}

    public static boolean showScreenOverlay(boolean vrSession,
                                            boolean chatScreen,
                                            boolean roomPanelReady) {
        // On desktop, retain text entry on all focused text-field screens.
        if (!vrSession) return true;
        // When the 3D panel works, avoid rendering the same keyboard twice.
        // When it does not, allow the chat keyboard to function as fallback.
        return chatScreen && !roomPanelReady;
    }
}

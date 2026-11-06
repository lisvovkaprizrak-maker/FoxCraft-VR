package dev.foxcraft.keyboard;

/**
 * Use the separate VR panel when available. When the Vivecraft bridge cannot
 * render in a menu, allow the same keyboard on the Minecraft GUI itself.
 * Never show an overlay without an active editable field.
 */
public final class KeyboardDisplayPolicy {
    private KeyboardDisplayPolicy() {}

    public static boolean showScreenOverlay(boolean vrSession,
                                            boolean chatScreen,
                                            boolean roomPanelReady) {
        return showScreenOverlay(vrSession, chatScreen, roomPanelReady, chatScreen);
    }

    public static boolean showScreenOverlay(boolean vrSession,
                                            boolean chatScreen,
                                            boolean roomPanelReady,
                                            boolean editableFocus) {
        return editableFocus && !roomPanelReady;
    }
}

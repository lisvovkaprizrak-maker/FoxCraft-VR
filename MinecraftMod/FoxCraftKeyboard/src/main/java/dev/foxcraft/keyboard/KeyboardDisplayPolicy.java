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
        return showScreenOverlay(vrSession, chatScreen, roomPanelReady,
            editableFocus, false);
    }

    /** Also used by the Vivecraft room-space panel. */
    public static boolean showRequested(boolean chatScreen,
                                        boolean editableFocus,
                                        boolean menuRequested) {
        return editableFocus && (chatScreen || menuRequested);
    }

    /** A menu needs explicit user input; Minecraft can auto-focus search. */
    public static boolean showScreenOverlay(boolean vrSession,
                                            boolean chatScreen,
                                            boolean roomPanelReady,
                                            boolean editableFocus,
                                            boolean menuRequested) {
        return !roomPanelReady && showRequested(chatScreen, editableFocus, menuRequested);
    }
}

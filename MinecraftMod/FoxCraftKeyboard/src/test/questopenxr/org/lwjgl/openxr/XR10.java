package org.lwjgl.openxr;
public final class XR10 {
    public static final int XR_TYPE_ACTION_STATE_GET_INFO = 48;
    public static final int XR_TYPE_ACTION_STATE_BOOLEAN = 50;
    public static int xrGetActionStateBoolean(XrSession session,
            XrActionStateGetInfo info, XrActionStateBoolean out) {
        out.active = session.active.contains(info.action.handle);
        out.pressed = session.pressed.contains(info.action.handle + ":" + info.subactionPath);
        return 0;
    }
}

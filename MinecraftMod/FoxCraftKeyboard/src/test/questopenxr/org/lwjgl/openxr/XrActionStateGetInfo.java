package org.lwjgl.openxr;
public final class XrActionStateGetInfo implements AutoCloseable {
    public XrAction action;
    public long subactionPath;
    public static XrActionStateGetInfo calloc() { return new XrActionStateGetInfo(); }
    public XrActionStateGetInfo type(int value) { return this; }
    public XrActionStateGetInfo action(XrAction value) { action = value; return this; }
    public XrActionStateGetInfo subactionPath(long value) { subactionPath = value; return this; }
    public void close() {}
}

package org.lwjgl.openxr;
public final class XrActionStateBoolean implements AutoCloseable {
    public boolean active;
    public boolean pressed;
    public static XrActionStateBoolean calloc() { return new XrActionStateBoolean(); }
    public XrActionStateBoolean type(int value) { return this; }
    public boolean isActive() { return active; }
    public boolean currentState() { return pressed; }
    public void close() {}
}

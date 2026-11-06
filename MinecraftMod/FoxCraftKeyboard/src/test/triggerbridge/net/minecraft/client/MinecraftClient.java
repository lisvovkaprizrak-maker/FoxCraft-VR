package net.minecraft.client;

import net.minecraft.client.option.KeyBinding;
/** Compile-only Minecraft client stub; not shipped in the Fabric mod. */
public final class MinecraftClient {
    private static final MinecraftClient INSTANCE = new MinecraftClient();
    public final Options options = new Options();

    public static MinecraftClient getInstance() { return INSTANCE; }

    public static final class Options {
        public KeyBinding attackKey = new KeyBinding("attack");
        public KeyBinding useKey = new KeyBinding("use");
    }
}

package dev.foxcraft.keyboard;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.glfw.GLFW;

/**
 * In-game keyboard rendered into vanilla Minecraft screens. Vivecraft handles
 * rendering those screens in VR. Does not depend on the Unity launcher.
 */
public final class VirtualKeyboardOverlay {
    private static final int PANEL_BG = 0xE9131014;
    private static final int KEY_BG = 0xED272125;
    private static final int KEY_HOVER = 0xFF73401C;
    private static final int KEY_PRESSED = 0xFFB54C0D;
    private static final int ORANGE = 0xFFFF981C;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MINI = 0xFFFFBB6D;

    private final KeyHoldController hold = new KeyHoldController();
    private KeyboardLayout.Panel panel;
    private Screen previousScreen;
    private boolean russian = true;
    private boolean symbols;
    private boolean shift;
    private boolean collapsed;

    public void render(Screen screen, DrawContext context, int mouseX, int mouseY) {
        if (screen != previousScreen) {
            previousScreen = screen;
            hold.cancel();
            collapsed = false;
        }

        if (focusedField(screen) == null) {
            panel = null;
            hold.cancel();
            return;
        }
        panel = KeyboardLayout.make(screen.width, screen.height,
            screen instanceof ChatScreen, russian, symbols, shift, collapsed);
        if (panel == null) return;

        long now = System.nanoTime() / 1_000_000L;
        int pulse = (int) (50 + 38 * Math.sin(now / 320.0));
        int border = 0xFF000000 | (Math.min(255, 180 + pulse) << 16) |
            (Math.min(255, 85 + pulse / 3) << 8) | 16;

        context.fill(panel.x() - 2, panel.y() - 2,
            panel.x() + panel.width() + 2, panel.y() + panel.height() + 2,
            border);
        context.fill(panel.x(), panel.y(),
            panel.x() + panel.width(), panel.y() + panel.height(), PANEL_BG);

        for (KeyboardLayout.Key key : panel.keys()) {
            boolean hovered = key.contains(mouseX, mouseY);
            boolean longHeld = hold.isHolding(key, now);
            int bg = longHeld ? KEY_PRESSED : (hovered ? KEY_HOVER : KEY_BG);
            context.fill(key.x(), key.y(), key.x() + key.width(),
                key.y() + key.height(), bg);

            if (longHeld) {
                context.fill(key.x(), key.y(), key.x() + key.width(),
                    key.y() + 2, ORANGE);
            }

            // Miniature legends make alternative long-press characters discoverable.
            if (!key.alternate().isEmpty() && key.height() >= 18) {
                int glyphWidth = MinecraftClient.getInstance().textRenderer.getWidth(key.alternate());
                context.drawText(MinecraftClient.getInstance().textRenderer,
                    key.alternate(), key.x() + key.width() - glyphWidth - 2,
                    key.y() + 1, MINI, false);
            }

            String caption = longHeld ? key.alternate() : key.label();
            if (caption.isEmpty()) caption = key.label();
            int captionWidth = MinecraftClient.getInstance().textRenderer.getWidth(caption);
            int tx = key.x() + Math.max(1, (key.width() - captionWidth) / 2);
            int ty = key.y() + Math.max(1, (key.height() - 8) / 2) +
                (!key.alternate().isEmpty() && key.height() >= 18 ? 3 : 0);
            context.drawText(MinecraftClient.getInstance().textRenderer,
                caption, tx, ty, TEXT, false);
        }

        // Gentle orange flow on the outer edge, advanced with wall-clock time.
        int sweep = panel.x() + (int) ((now / 12L) % Math.max(1, panel.width()));
        context.fill(sweep, panel.y() - 2, Math.min(panel.x() + panel.width(), sweep + 20),
            panel.y(), ORANGE);
    }

    /** Returns true if the key press was captured, preventing screen click-through. */
    public boolean mousePressed(Screen screen, double x, double y, int button) {
        if (screen != previousScreen || button != GLFW.GLFW_MOUSE_BUTTON_LEFT ||
            panel == null || focusedField(screen) == null)
            return false;
        if (!panel.contains(x, y)) return false;
        KeyboardLayout.Key key = panel.at(x, y);
        if (key != null && key.command() != KeyboardLayout.Command.NONE)
            hold.press(key, System.nanoTime() / 1_000_000L);
        else
            hold.cancel();
        return true; // Also swallow clicks on panel gaps.
    }

    /** Returns true if captured; letters are committed only when released. */
    public boolean mouseReleased(Screen screen, double x, double y, int button) {
        if (screen != previousScreen) {
            hold.cancel();
            return false;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !hold.hasPending()) return false;
        KeyboardLayout.Key releasedOn = panel != null ? panel.at(x, y) : null;
        KeyHoldController.Activation activation = hold.release(releasedOn, System.nanoTime() / 1_000_000L);
        if (activation != null && focusedField(screen) != null)
            activate(screen, activation);
        return true;
    }

    private void activate(Screen screen, KeyHoldController.Activation result) {
        TextFieldWidget field = focusedField(screen);
        if (field == null) return;

        switch (result.key().command()) {
            case TYPE, SPACE -> field.write(result.text());
            case BACKSPACE -> field.eraseCharacters(-1);
            case LEFT -> field.moveCursor(-1, false);
            case RIGHT -> field.moveCursor(1, false);
            case ENTER -> screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
            case LANGUAGE -> russian = !russian;
            case SYMBOLS -> symbols = !symbols;
            case SHIFT -> shift = !shift;
            case COLLAPSE -> collapsed = true;
            case EXPAND -> collapsed = false;
            default -> { }
        }
        if (result.key().command() == KeyboardLayout.Command.TYPE && shift)
            shift = false;
    }

    private static TextFieldWidget focusedField(Screen screen) {
        if (screen == null) return null;
        Element focused = screen.getFocused();
        // Only handle editable, focused fields. Never steal player movement or
        // send text to chat when the player is not actually editing.
        if (focused instanceof TextFieldWidget field && field.isVisible())
            return field;
        return null;
    }
}

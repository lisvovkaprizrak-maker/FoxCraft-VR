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
    private boolean capturedPress;
    private KeyboardLayout.Panel panel;
    private Screen previousScreen;
    private int layoutWidth = -1;
    private int layoutHeight = -1;
    private boolean layoutChat;
    private boolean layoutRussian;
    private boolean layoutSymbols;
    private boolean layoutShift;
    private boolean layoutCollapsed;
    private boolean russian = true;
    private boolean symbols;
    private boolean shift;
    private boolean collapsed;

    public void render(Screen screen, DrawContext context, int mouseX, int mouseY) {
        if (screen != previousScreen) {
            previousScreen = screen;
            hold.cancel();
            capturedPress = false;
            collapsed = false;
            panel = null;
        }

        if (focusedField(screen) == null) {
            panel = null;
            hold.cancel();
            return;
        }
        boolean chat = screen instanceof ChatScreen;
        if (panel == null || layoutWidth != screen.width || layoutHeight != screen.height ||
            layoutChat != chat || layoutRussian != russian || layoutSymbols != symbols ||
            layoutShift != shift || layoutCollapsed != collapsed) {
            panel = KeyboardLayout.make(screen.width, screen.height,
                chat, russian, symbols, shift, collapsed);
            layoutWidth = screen.width;
            layoutHeight = screen.height;
            layoutChat = chat;
            layoutRussian = russian;
            layoutSymbols = symbols;
            layoutShift = shift;
            layoutCollapsed = collapsed;
        }
        if (panel == null) return;

        long now = System.nanoTime() / 1_000_000L;
        int pulse = (int) (50 + 38 * Math.sin(now / 320.0));
        int border = 0xFF000000 | (Math.min(255, 180 + pulse) << 16) |
            (Math.min(255, 85 + pulse / 3) << 8) | 16;

        // Two translucent rings and a warm animated inner border.
        context.fill(panel.x() - 4, panel.y() - 4,
            panel.x() + panel.width() + 4, panel.y() + panel.height() + 4,
            0x44FF7400);
        context.fill(panel.x() - 2, panel.y() - 2,
            panel.x() + panel.width() + 2, panel.y() + panel.height() + 2,
            border);
        context.fill(panel.x(), panel.y(),
            panel.x() + panel.width(), panel.y() + panel.height(), PANEL_BG);

        var font = MinecraftClient.getInstance().textRenderer;
        KeyboardLayout.Key heldAlternate = null;
        for (KeyboardLayout.Key key : panel.keys()) {
            boolean hovered = key.contains(mouseX, mouseY);
            boolean longHeld = hold.isHolding(key, now);
            int bg = longHeld ? KEY_PRESSED : (hovered ? KEY_HOVER : KEY_BG);
            context.fill(key.x(), key.y(), key.x() + key.width(),
                key.y() + key.height(), bg);

            float progress = hold.progress(key, now);
            if (progress > 0f) {
                int bar = Math.max(1, Math.round(key.width() * progress));
                context.fill(key.x(), key.y() + key.height() - 2,
                    Math.min(key.x() + key.width(), key.x() + bar),
                    key.y() + key.height(), ORANGE);
            }
            if (longHeld) {
                context.fill(key.x(), key.y(), key.x() + key.width(),
                    key.y() + 2, ORANGE);
                heldAlternate = key;
            }

            // Miniature legends make alternative long-press characters discoverable.
            if (!key.alternate().isEmpty() && key.height() >= 18) {
                int glyphWidth = font.getWidth(key.alternate());
                context.drawText(font,
                    key.alternate(), key.x() + key.width() - glyphWidth - 2,
                    key.y() + 1, MINI, false);
            }

            String caption = longHeld ? key.alternate() : key.label();
            if (caption.isEmpty()) caption = key.label();
            int captionWidth = font.getWidth(caption);
            int tx = key.x() + Math.max(1, (key.width() - captionWidth) / 2);
            int ty = key.y() + Math.max(1, (key.height() - 8) / 2) +
                (!key.alternate().isEmpty() && key.height() >= 18 ? 3 : 0);
            context.drawText(font,
                caption, tx, ty, TEXT, false);
        }

        // The symbol is displayed above the held key before release; the game
        // receives exactly one character after the controller trigger is released.
        if (heldAlternate != null && !heldAlternate.alternate().isEmpty()) {
            String symbol = heldAlternate.alternate();
            int width = Math.max(23, font.getWidth(symbol) + 12);
            int popupX = heldAlternate.x() + (heldAlternate.width() - width) / 2;
            int popupY = Math.max(1, heldAlternate.y() - 24);
            context.fill(popupX - 2, popupY - 2,
                popupX + width + 2, popupY + 20, ORANGE);
            context.fill(popupX, popupY, popupX + width, popupY + 18, KEY_BG);
            context.drawText(font, symbol,
                popupX + (width - font.getWidth(symbol)) / 2,
                popupY + 5, TEXT, false);
        }

        // Gentle orange flow on the outer edge, advanced with monotonic time.
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
        // Capture both halves of a pointer gesture, including panel margins:
        // without this a press on a gap can leak its release into Minecraft UI.
        capturedPress = true;
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
            capturedPress = false;
            return false;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !capturedPress) return false;
        capturedPress = false;
        KeyboardLayout.Key releasedOn = panel != null ? panel.at(x, y) : null;
        KeyHoldController.Activation activation = hold.release(
            releasedOn, System.nanoTime() / 1_000_000L);
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

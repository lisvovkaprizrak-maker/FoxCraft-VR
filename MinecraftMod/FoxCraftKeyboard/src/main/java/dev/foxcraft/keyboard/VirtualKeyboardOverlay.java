package dev.foxcraft.keyboard;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.glfw.GLFW;
import java.util.List;
import java.util.Locale;

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

    private final KeyboardPointerInput pointer = new KeyboardPointerInput();
    private final KeyboardPointerInput leftPointer = new KeyboardPointerInput();
    private final VivecraftLeftPointer leftRay = new VivecraftLeftPointer();
    private final KeyboardPreferences prefs = new KeyboardPreferences(
        FabricLoader.getInstance().getConfigDir().resolve("foxcraft-keyboard-size.txt"));
    // Candidates belong to the most recent word and exactly one text-field state.
    // Editing or moving the cursor invalidates replacement to avoid data loss.
    private List<String> suggestions = List.of();
    private TextFieldWidget suggestionField;
    private String suggestionSnapshot = "";
    private String lastInsertedWord = "";
    private int suggestionCursor = -1;
    private boolean leftDown;
    private VivecraftLeftPointer.Sample leftSample;
    private KeyboardLayout.Panel panel;
    private Screen previousScreen;
    private int layoutWidth = -1;
    private int layoutPreferredWidth = -1;
    private int preferredWidth = prefs.loadWidth();
    private int layoutHeight = -1;
    private int layoutChatInputBottomY = -1;
    private boolean layoutChat;
    private boolean layoutDetached;
    private boolean layoutRussian;
    private boolean layoutSymbols;
    private boolean layoutShift;
    private boolean layoutCollapsed;
    private boolean russian = true;
    private boolean symbols;
    private boolean shift;
    private boolean collapsed;
    private boolean suppressMinecraftGuiClick;

    /** The detached controller ray is only valid inside our visible panel. */
    public boolean detachedContains(double x, double y) {
        return layoutDetached && panel != null && panel.contains(x, y);
    }

    public void render(Screen screen, DrawContext context, int mouseX, int mouseY) {
        if (screen instanceof ChatScreen && FoxCraftVrPlane.isReady()) return;
        renderInternal(screen, context, mouseX, mouseY,
            screen.width, screen.height, false, -100, -100);
    }

    /** Same keyboard controls, painted on the separate QuestCraft-aligned VR quad. */
    public void renderDetached(Screen screen, DrawContext context,
                               int mouseX, int mouseY, int width, int height,
                               double leftX, double leftY) {
        renderInternal(screen, context, mouseX, mouseY,
            width, height, true, leftX, leftY);
    }

    private void renderInternal(Screen screen, DrawContext context,
                                int mouseX, int mouseY, int width,
                                int height, boolean detached,
                                double detachedLeftX, double detachedLeftY) {
        if (screen != previousScreen) {
            previousScreen = screen;
            clearSuggestions();
            pointer.cancel();
            leftPointer.cancel();
            leftDown = false;
            leftSample = null;
            suppressMinecraftGuiClick = false;
            collapsed = false;
            panel = null;
        }

        if (detached) {
            leftSample = new VivecraftLeftPointer.Sample(
                detachedLeftX >= 0 && detachedLeftY >= 0,
                detachedLeftX, detachedLeftY, false);
        }

        if (focusedField(screen) == null) {
            clearSuggestions();
            panel = null;
            pointer.cancel();
            leftPointer.cancel();
            leftDown = false;
            leftSample = null;
            return;
        }
        // Never replace words after edits, cursor moves, or focus changes.
        TextFieldWidget activeField = focusedField(screen);
        if (suggestionField != null && (suggestionField != activeField ||
            !suggestionSnapshot.equals(activeField.getText()) ||
            suggestionCursor != activeField.getCursor() ||
            !activeField.getSelectedText().isEmpty()))
            clearSuggestions();
        boolean chat = screen instanceof ChatScreen;
        // Use the actual lower edge of the chat field. The keyboard occupies
        // the free area below it instead of being drawn inside the text box.
        int chatInputBottomY = chat && !detached
            ? activeField.getY() + activeField.getHeight() : -1;
        if (panel == null || layoutWidth != width || layoutHeight != height ||
            layoutChatInputBottomY != chatInputBottomY ||
            layoutChat != chat || layoutDetached != detached ||
            layoutPreferredWidth != preferredWidth ||
            layoutRussian != russian || layoutSymbols != symbols ||
            layoutShift != shift || layoutCollapsed != collapsed) {
            panel = KeyboardLayout.make(width, height,
                chat && !detached, russian, symbols, shift, collapsed,
                preferredWidth, suggestions, chatInputBottomY, detached);
            layoutWidth = width;
            layoutPreferredWidth = preferredWidth;
            layoutHeight = height;
            layoutChatInputBottomY = chatInputBottomY;
            layoutChat = chat;
            layoutDetached = detached;
            layoutRussian = russian;
            layoutSymbols = symbols;
            layoutShift = shift;
            layoutCollapsed = collapsed;
        }
        if (panel == null) return;

        // Vivecraft routes controller ray movement through the Minecraft
        // screen mouse coordinates, including while the trigger is held.
        pointer.move(panel, mouseX, mouseY, focusedField(screen));

        long now = System.nanoTime() / 1_000_000L;
        if (!detached) updateLeftHand(screen, now);
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
            boolean hovered = key.contains(mouseX, mouseY) ||
                (leftSample != null && leftSample.valid() &&
                    key.contains(leftSample.x(), leftSample.y()));
            boolean longHeld = pointer.isHolding(key, now) ||
                leftPointer.isHolding(key, now);
            // Immediate key-down feedback, before the long-press threshold.
            int bg = (pointer.isPressed(key) || leftPointer.isPressed(key) || longHeld)
                ? KEY_PRESSED
                : (hovered ? KEY_HOVER : KEY_BG);
            context.fill(key.x(), key.y(), key.x() + key.width(),
                key.y() + key.height(), bg);

            float progress = Math.max(pointer.progress(key, now),
                leftPointer.progress(key, now));
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
            if (key.command() == KeyboardLayout.Command.SUGGESTION) {
                // Visually truncate only: activation stores the full word.
                String full = caption;
                while (caption.length() > 1 && font.getWidth(caption) > key.width() - 4)
                    caption = caption.substring(0, caption.length()-1);
                if (!caption.equals(full) && caption.length() > 1)
                    caption = caption.substring(0, caption.length()-1) + "…";
            }
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
            int popupWidth = Math.max(23, font.getWidth(symbol) + 12);
            int popupX = heldAlternate.x() + (heldAlternate.width() - popupWidth) / 2;
            int popupY = Math.max(1, heldAlternate.y() - 24);
            context.fill(popupX - 2, popupY - 2,
                popupX + popupWidth + 2, popupY + 20, ORANGE);
            context.fill(popupX, popupY, popupX + popupWidth, popupY + 18, KEY_BG);
            context.drawText(font, symbol,
                popupX + (popupWidth - font.getWidth(symbol)) / 2,
                popupY + 5, TEXT, false);
        }

        // Explicit left-hand marker helps verify that the second controller
        // ray tracks independently from Vivecraft's right-hand mouse cursor.
        if (leftSample != null && leftSample.valid() &&
            panel.contains(leftSample.x(), leftSample.y())) {
            int lx = (int) leftSample.x();
            int ly = (int) leftSample.y();
            context.fill(lx - 3, ly - 3, lx + 4, ly + 4, ORANGE);
            context.drawText(font, "L", lx + 6, ly - 5, MINI, false);
        }

        // A swipe shows its gathered letters live, but writes none of them
        // into chat until the trigger is released.
        String preview = !leftPointer.swipePreview().isEmpty()
            ? leftPointer.swipePreview() : pointer.swipePreview();
        if (!preview.isEmpty()) {
            String visible = preview.length() > 26 ? "..." + preview.substring(preview.length() - 26) : preview;
            int previewWidth = Math.min(panel.width() - 12, font.getWidth(visible) + 16);
            int previewX = panel.x() + (panel.width() - previewWidth) / 2;
            int previewY = Math.max(1, panel.y() - 23);
            context.fill(previewX, previewY, previewX + previewWidth, previewY + 20, KEY_BG);
            context.drawText(font, visible,
                previewX + 8, previewY + 5, ORANGE, false);
        }

        // Gentle orange flow on the outer edge, advanced with monotonic time.
        int sweep = panel.x() + (int) ((now / 12L) % Math.max(1, panel.width()));
        context.fill(sweep, panel.y() - 2, Math.min(panel.x() + panel.width(), sweep + 20),
            panel.y(), ORANGE);
    }

    /**
     * Vivecraft's normal screen mouse events represent only its primary
     * GUI pointer. Poll the independent second ray here so either controller
     * can interact with FoxCraft without duplicating right-hand mouse input.
     */
    private void updateLeftHand(Screen screen, long nowMs) {
        leftSample = leftRay.poll(screen.width, screen.height);
        if (leftSample == null) {
            leftPointer.cancel();
            leftDown = false;
            return;
        }

        TextFieldWidget field = focusedField(screen);
        boolean pressed = leftSample.pressed();
        double x = leftSample.valid() ? leftSample.x() : -100;
        double y = leftSample.valid() ? leftSample.y() : -100;

        if (pressed && !leftDown && leftSample.valid()) {
            leftPointer.press(panel, x, y, nowMs, field);
        } else if (pressed && leftDown) {
            leftPointer.move(panel, x, y, field);
        } else if (!pressed && leftDown) {
            KeyboardPointerInput.Release release = leftPointer.release(
                panel, x, y, nowMs, field);
            if (release.activation() != null && field != null)
                applyRelease(screen, release);
        }
        leftDown = pressed;
    }

    /** VR-plane controller input; separate state machines for each hand. */
    public void vrPressed(Screen screen, double x, double y, int hand) {
        if (focusedField(screen) != null)
            handPointer(hand).press(panel, x, y,
                System.nanoTime() / 1_000_000L, focusedField(screen));
    }

    public void vrMoved(Screen screen, double x, double y, int hand) {
        handPointer(hand).move(panel, x, y, focusedField(screen));
    }

    public void vrReleased(Screen screen, double x, double y, int hand) {
        KeyboardPointerInput.Release release = handPointer(hand).release(
            panel, x, y, System.nanoTime() / 1_000_000L, focusedField(screen));
        if (release.activation() != null && focusedField(screen) != null)
            applyRelease(screen, release);
    }

    private KeyboardPointerInput handPointer(int hand) {
        return hand == 1 ? leftPointer : pointer;
    }

    /** Returns true if the gesture is captured (including key gaps). */
    public boolean mousePressed(Screen screen, double x, double y, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (FoxCraftVrPlane.isReady()) {
            // Vivecraft may also project the same trigger to the original
            // Minecraft GUI. Do not let it click through our separate VR quad.
            suppressMinecraftGuiClick = FoxCraftVrPlane.interceptsMainGuiMouse();
            return suppressMinecraftGuiClick;
        }
        if (screen != previousScreen || focusedField(screen) == null)
            return false;
        return pointer.press(panel, x, y, System.nanoTime() / 1_000_000L,
            focusedField(screen));
    }

    /** A matching release is swallowed even if the pointer left the panel. */
    public boolean mouseReleased(Screen screen, double x, double y, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (FoxCraftVrPlane.isReady() || suppressMinecraftGuiClick) {
            boolean suppress = suppressMinecraftGuiClick ||
                FoxCraftVrPlane.interceptsMainGuiMouse();
            suppressMinecraftGuiClick = false;
            if (suppress) return true;
            if (FoxCraftVrPlane.isReady()) return false;
        }
        if (screen != previousScreen) {
            pointer.cancel();
            return false;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;

        KeyboardPointerInput.Release release = pointer.release(
            panel, x, y, System.nanoTime() / 1_000_000L, focusedField(screen));
        if (release.activation() != null && focusedField(screen) != null)
            applyRelease(screen, release);
        return release.consumed();
    }

    /** Swipe candidates are bound to the exact inserted word and cursor. */
    private void applyRelease(Screen screen, KeyboardPointerInput.Release release) {
        KeyHoldController.Activation result = release.activation();
        TextFieldWidget field = focusedField(screen);
        if (result == null || field == null) return;
        if (result.key().command() == KeyboardLayout.Command.TYPE &&
            !release.suggestions().isEmpty()) {
            clearSuggestions();
            String chosen = shift ? capitalize(result.text()) : result.text();
            List<String> choices = shift
                ? release.suggestions().stream()
                    .map(VirtualKeyboardOverlay::capitalize).toList()
                : release.suggestions();
            shift = false;
            int before = field.getCursor();
            field.write(chosen);
            int after = field.getCursor();
            String actual = field.getText();
            if (after - before == chosen.length() &&
                after <= actual.length() &&
                actual.substring(before, after).equals(chosen)) {
                suggestions = choices;
                suggestionField = field;
                suggestionSnapshot = actual;
                suggestionCursor = after;
                lastInsertedWord = chosen;
                panel = null;
            }
        } else activate(screen, result);
    }

    private static String capitalize(String word) {
        return word.isEmpty() ? word :
            word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1);
    }

    private void clearSuggestions() {
        if (!suggestions.isEmpty()) panel = null;
        suggestions = List.of();
        suggestionField = null;
        suggestionSnapshot = "";
        suggestionCursor = -1;
        lastInsertedWord = "";
    }

    private void activate(Screen screen, KeyHoldController.Activation result) {
        TextFieldWidget field = focusedField(screen);
        if (field == null) return;

        if (result.key().command() == KeyboardLayout.Command.SUGGESTION) {
            String candidate = result.text();
            if (field == suggestionField &&
                field.getText().equals(suggestionSnapshot) &&
                field.getCursor() == suggestionCursor &&
                field.getSelectedText().isEmpty() &&
                suggestionCursor >= lastInsertedWord.length() &&
                field.getText().substring(suggestionCursor - lastInsertedWord.length(),
                    suggestionCursor).equals(lastInsertedWord) &&
                suggestions.contains(candidate)) {
                field.eraseCharacters(-lastInsertedWord.length());
                field.write(candidate);
            }
            clearSuggestions();
            return;
        }
        clearSuggestions();
        switch (result.key().command()) {
            case TYPE, SPACE -> field.write(result.text());
            case BACKSPACE -> field.eraseCharacters(-1);
            case LEFT -> field.moveCursor(-1, false);
            case RIGHT -> field.moveCursor(1, false);
            case ENTER -> screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
            case ESCAPE -> screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            case LANGUAGE -> russian = !russian;
            case SYMBOLS -> symbols = !symbols;
            case RESIZE -> {
                preferredWidth = KeyboardPreferences.nextWidth(preferredWidth);
                prefs.saveWidth(preferredWidth);
            }
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

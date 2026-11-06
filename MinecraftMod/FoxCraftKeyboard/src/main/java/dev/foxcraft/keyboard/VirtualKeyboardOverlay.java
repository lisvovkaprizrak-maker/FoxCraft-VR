package dev.foxcraft.keyboard;

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
    private static final int PANEL_BG = 0xF21C1B23;
    private static final int KEY_BG = 0xFF302E3A;
    private static final int KEY_HOVER = 0xFFB45215;
    private static final int KEY_PRESSED = 0xFFFF8B16;
    private static final int ORANGE = 0xFFFF8D20;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MINI = 0xFFFFBB6D;

    private final KeyboardPointerInput pointer = new KeyboardPointerInput();
    private final KeyboardPointerInput leftPointer = new KeyboardPointerInput();
    private final VivecraftLeftPointer leftRay = new VivecraftLeftPointer();
    // Size is deliberately locked for the reference design. No preference
    // file is loaded, and there is no resize key or gesture.
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
    // Menus often auto-focus their search box; only show the screen fallback
    // after a user explicitly clicks an editable field.
    private Screen activatedMenuScreen;
    private TextFieldWidget activatedMenuField;
    private TextFieldWidget layoutFocusedField;
    private int layoutFieldX = -1, layoutFieldY = -1;
    private int layoutFieldWidth = -1, layoutFieldHeight = -1;
    private int layoutWidth = -1;
    private int layoutPreferredWidth = -1;
    private static final int FIXED_REFERENCE_WIDTH = 390;
    private int preferredWidth = FIXED_REFERENCE_WIDTH;
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
    private boolean settingsOpen;
    private boolean layoutSettingsOpen;
    private KeyboardTheme theme = KeyboardTheme.CLASSIC;
    private boolean suppressMinecraftGuiClick;

    /**
     * A visible focused text field alone does not imply a keyboard request.
     * World/server lists often auto-focus search widgets on screen creation.
     * Both the embedded fallback and the independent VR surface must obey
     * the same explicit-menu-click rule.
     */
    public boolean shouldDisplayForScreen(Screen screen) {
        TextFieldWidget field = focusedField(screen);
        boolean menuRequested = screen == activatedMenuScreen
            && field != null && field == activatedMenuField;
        return KeyboardDisplayPolicy.showRequested(
            screen instanceof ChatScreen, field != null, menuRequested);
    }

    /** The detached controller ray is only valid inside our visible panel. */
    public boolean detachedContains(double x, double y) {
        return layoutDetached && panel != null && panel.contains(x, y);
    }

    public void render(Screen screen, DrawContext context, int mouseX, int mouseY) {
        // Auto-focused search fields must not paint FoxCraft over world lists
        // and menu buttons. Outside chat, explicitly clicking a field arms it.
        if (FoxCraftVrPlane.isReady() || !shouldDisplayForScreen(screen))
            return;
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
            settingsOpen = false;
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
            layoutFocusedField != activeField ||
            layoutFieldX != activeField.getX() ||
            layoutFieldY != activeField.getY() ||
            layoutFieldWidth != activeField.getWidth() ||
            layoutFieldHeight != activeField.getHeight() ||
            layoutChatInputBottomY != chatInputBottomY ||
            layoutChat != chat || layoutDetached != detached ||
            layoutPreferredWidth != preferredWidth ||
            layoutRussian != russian || layoutSymbols != symbols ||
            layoutShift != shift || layoutCollapsed != collapsed ||
            layoutSettingsOpen != settingsOpen) {
            panel = KeyboardLayout.make(width, height,
                chat && !detached, russian, symbols, shift, collapsed,
                preferredWidth, suggestions, chatInputBottomY, detached);
            if (settingsOpen && panel != null) {
                panel = KeyboardLayout.themeMenu(panel, theme.name());
            }
            if (!chat && !detached && panel != null) {
                panel = KeyboardLayout.dockForMenu(panel, width, height,
                    activeField.getX(), activeField.getY(),
                    activeField.getWidth(), activeField.getHeight());
            }
            layoutFocusedField = activeField;
            layoutFieldX = activeField.getX();
            layoutFieldY = activeField.getY();
            layoutFieldWidth = activeField.getWidth();
            layoutFieldHeight = activeField.getHeight();
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
            layoutSettingsOpen = settingsOpen;
        }
        if (panel == null) return;
        int panelBg = theme.panel();
        int keyBg = theme.key();
        int keyHover = theme.hover();
        int keyPressed = theme.pressed();
        int accent = theme.accent();
        int textColor = theme.text();
        int legendColor = theme.legend();

        // Vivecraft routes controller ray movement through the Minecraft
        // screen mouse coordinates, including while the trigger is held.
        pointer.move(panel, mouseX, mouseY, focusedField(screen));

        long now = System.nanoTime() / 1_000_000L;
        if (!detached) updateLeftHand(screen, now);
        int pulse = (int) (50 + 38 * Math.sin(now / 320.0));
        int border = accent;

        // HD-inspired multilayer chassis: diffuse light, cool top rim,
        // smooth alpha-covered corners and a restrained charcoal gradient.
        for (int layer = 6; layer >= 1; layer--) {
            int alpha = Math.max(1, (20 - layer * 2));
            rounded(context, panel.x() - layer, panel.y() - layer,
                panel.width() + layer * 2, panel.height() + layer * 2,
                14 + layer, (alpha << 24) | (accent & 0x00FFFFFF));
        }
        rounded(context, panel.x() - 2, panel.y() - 2,
            panel.width() + 4, panel.height() + 4, 13, border);
        rounded(context, panel.x(), panel.y(),
            panel.width(), panel.height(), 12, panelBg);
        // A soft vertical charcoal finish similar to the reference photo.
        for (int strip = 0; strip < 6; strip++) {
            int inset = 3 + strip;
            rounded(context, panel.x() + inset, panel.y() + inset,
                panel.width() - 2 * inset, Math.max(2, panel.height() / 4),
                10, ((8 - strip) << 24) | (theme == KeyboardTheme.CLASSIC ? 0x00B3A5C2 : (accent & 0x00FFFFFF)));
        }
        context.fill(panel.x() + 21, panel.y() - 2,
            panel.x() + panel.width() - 21, panel.y() - 1, ((accent & 0x00FFFFFF) | 0x66000000));
        // Raised center crest: the reference keyboard has a fox-shaped top
        // silhouette rather than a simple uniformly rounded rectangle.
        if (!collapsed) {
            int crestW = Math.min(128, panel.width() / 2);
            int crestX = panel.x() + (panel.width() - crestW) / 2;
            rounded(context, crestX - 3, panel.y() - 5,
                crestW + 6, 27, 10, border);
            rounded(context, crestX - 1, panel.y() - 3,
                crestW + 2, 26, 9, panelBg);
        }
        if (!collapsed && theme == KeyboardTheme.CLASSIC) {
            drawChecker(context, panel.x() + 5, panel.y() + 5);
            drawChecker(context, panel.x() + panel.width() - 14,
                panel.y() + panel.height() - 14);
        } else if (!collapsed && theme == KeyboardTheme.NEW_YEAR) {
            drawSnowDots(context, panel, now);
        }

        var font = MinecraftClient.getInstance().textRenderer;
        // Branding is decoration, never a controller-hit-test key.
        if (!collapsed) {
            int brandY = panel.y() + 4;
            int brandX = panel.x() + (panel.width() - 92) / 2;
            if (theme == KeyboardTheme.CLASSIC) {
                drawFoxBadge(context, brandX, brandY);
                context.drawText(font, ("Fox"), brandX + 20, brandY + 4,
                    accent, true);
                context.drawText(font, ("Craft"), brandX + 38, brandY + 4,
                    textColor, true);
            } else {
                String title = settingsOpen ? "ТЕМЫ" : theme.title();
                int titleWidth = font.getWidth(title);
                context.drawText(font, title,
                    panel.x() + (panel.width() - titleWidth) / 2,
                    brandY + 6, accent, false);
            }
        }
        KeyboardLayout.Key heldAlternate = null;
        for (KeyboardLayout.Key key : panel.keys()) {
            boolean hovered = key.contains(mouseX, mouseY) ||
                (leftSample != null && leftSample.valid() &&
                    key.contains(leftSample.x(), leftSample.y()));
            boolean longHeld = pointer.isHolding(key, now) ||
                leftPointer.isHolding(key, now);
            // Immediate key-down feedback, before the long-press threshold.
            int bg = (pointer.isPressed(key) || leftPointer.isPressed(key) || longHeld)
                ? keyPressed
                : (hovered ? keyHover : keyBg);
            boolean action = key.command() == KeyboardLayout.Command.ENTER;
            boolean activeLanguage = key.command() == KeyboardLayout.Command.LANGUAGE_RU ||
                key.command() == KeyboardLayout.Command.LANGUAGE_EN;
            boolean shiftKey = key.command() == KeyboardLayout.Command.SHIFT && shift;
            if (action && !hovered && !longHeld) bg = theme.enter();
            if (activeLanguage && !hovered && !longHeld)
                bg = ((russian && key.command() == KeyboardLayout.Command.LANGUAGE_RU) ||
                      (!russian && key.command() == KeyboardLayout.Command.LANGUAGE_EN))
                    ? theme.enter() : keyBg;
            if (shiftKey && !hovered && !longHeld) bg = keyHover;
            if (key.command() == KeyboardLayout.Command.SYMBOLS &&
                !hovered && !longHeld && symbols) bg = keyHover;
            if (key.command() == KeyboardLayout.Command.THEME) {
                KeyboardTheme option = KeyboardTheme.fromName(key.value());
                bg = option == theme ? option.hover() : option.key();
            }
            // Rounded raised keycaps with a soft contact shadow, edge
            // highlight and amber bloom only under pointer/press.
            boolean lit = hovered || longHeld || pointer.isPressed(key) ||
                leftPointer.isPressed(key);
            rounded(context, key.x(), key.y() + 2,
                key.width(), key.height(), 5, 0x55000000);
            if (lit) {
                rounded(context, key.x() - 2, key.y() - 2,
                    key.width() + 4, key.height() + 4, 7, (0x55000000 | (accent & 0x00FFFFFF)));
                rounded(context, key.x() - 1, key.y() - 1,
                    key.width() + 2, key.height() + 2, 6, accent);
            } else {
                rounded(context, key.x(), key.y(),
                    key.width(), key.height(), 6, (theme == KeyboardTheme.CLASSIC ? 0xFF494553 : accent));
            }
            rounded(context, key.x() + 1, key.y() + 1,
                Math.max(1, key.width() - 2), Math.max(1, key.height() - 2),
                5, bg);
            if (key.command() == KeyboardLayout.Command.THEME) {
                KeyboardTheme option = KeyboardTheme.fromName(key.value());
                rounded(context, key.x() + 4, key.y() + 3,
                    14, Math.max(4, key.height() - 6), 3, option.accent());
            }
            // Subtle glossy top edge gives dimension even without textures.
            if (key.width() >= 10 && key.height() >= 8)
                context.fill(key.x() + 5, key.y() + 1,
                    key.x() + key.width() - 5, key.y() + 2,
                    lit ? 0x66FFF1CE : 0x355F5B70);

            float progress = Math.max(pointer.progress(key, now),
                leftPointer.progress(key, now));
            if (progress > 0f) {
                int bar = Math.max(1, Math.round(key.width() * progress));
                context.fill(key.x(), key.y() + key.height() - 2,
                    Math.min(key.x() + key.width(), key.x() + bar),
                    key.y() + key.height(), accent);
            }
            if (longHeld) {
                context.fill(key.x(), key.y(), key.x() + key.width(),
                    key.y() + 2, accent);
                heldAlternate = key;
            }

            // Miniature legends make alternative long-press characters discoverable.
            if (!key.alternate().isEmpty() && key.height() >= 18) {
                int glyphWidth = font.getWidth((key.alternate()));
                context.drawText(font,
                    (key.alternate()), key.x() + key.width() - glyphWidth - 2,
                    key.y() + 1, legendColor, false);
            }

            String caption = longHeld ? key.alternate() : key.label();
            if (caption.isEmpty()) caption = key.label();
            if (key.command() == KeyboardLayout.Command.SUGGESTION) {
                // Visually truncate only: activation stores the full word.
                String full = caption;
                while (caption.length() > 1 && font.getWidth((caption)) > key.width() - 4)
                    caption = caption.substring(0, caption.length()-1);
                if (!caption.equals(full) && caption.length() > 1)
                    caption = caption.substring(0, caption.length()-1) + "…";
            }
            if (key.command() == KeyboardLayout.Command.THEME) caption = "      " + caption;
            int captionWidth = font.getWidth((caption));
            int tx = key.x() + Math.max(1, (key.width() - captionWidth) / 2);
            int ty = key.y() + Math.max(1, (key.height() - 8) / 2) +
                (!key.alternate().isEmpty() && key.height() >= 18 ? 3 : 0);
            context.drawText(font,
                (caption), tx, ty, textColor, false);
        }

        // The symbol is displayed above the held key before release; the game
        // receives exactly one character after the controller trigger is released.
        if (heldAlternate != null && !heldAlternate.alternate().isEmpty()) {
            String symbol = heldAlternate.alternate();
            int popupWidth = Math.max(23, font.getWidth((symbol)) + 12);
            int popupX = heldAlternate.x() + (heldAlternate.width() - popupWidth) / 2;
            int popupY = Math.max(1, heldAlternate.y() - 24);
            context.fill(popupX - 2, popupY - 2,
                popupX + popupWidth + 2, popupY + 20, accent);
            context.fill(popupX, popupY, popupX + popupWidth, popupY + 18, keyBg);
            context.drawText(font, (symbol),
                popupX + (popupWidth - font.getWidth((symbol))) / 2,
                popupY + 5, textColor, false);
        }

        // Explicit left-hand marker helps verify that the second controller
        // ray tracks independently from Vivecraft's right-hand mouse cursor.
        if (leftSample != null && leftSample.valid() &&
            panel.contains(leftSample.x(), leftSample.y())) {
            int lx = (int) leftSample.x();
            int ly = (int) leftSample.y();
            // Compact circular marker at the actual left-hand hit point.
            drawAimCircle(context, lx, ly);
        }

        // The right controller is the primary GUI pointer. In detached VR
        // mode its position is projected independently of the left marker.
        // Draw both so missing native QuestCraft rays are immediately obvious.
        if (panel.contains(mouseX, mouseY)) {
            // Both controllers use simple circles, never L / R labels.
            drawAimCircle(context, mouseX, mouseY);
        }

        // A swipe shows its gathered letters live, but writes none of them
        // into chat until the trigger is released.
        String preview = !leftPointer.swipePreview().isEmpty()
            ? leftPointer.swipePreview() : pointer.swipePreview();
        if (!preview.isEmpty()) {
            String visible = preview.length() > 26 ? "..." + preview.substring(preview.length() - 26) : preview;
            int previewWidth = Math.min(panel.width() - 12, font.getWidth((visible)) + 16);
            int previewX = panel.x() + (panel.width() - previewWidth) / 2;
            int previewY = Math.max(1, panel.y() - 23);
            context.fill(previewX, previewY, previewX + previewWidth, previewY + 20, keyBg);
            context.drawText(font, (visible),
                previewX + 8, previewY + 5, accent, false);
        }

        // Light stays on the chassis edge; no moving bar crossing the logo.
        int glow = (int) (30 + 20 * (1 + Math.sin(now / 500.0)));
        context.fill(panel.x() + 12, panel.y() + panel.height() - 2,
            panel.x() + panel.width() - 12, panel.y() + panel.height() - 1,
            (Math.min(255, glow) << 24) | (accent & 0x00FFFFFF));
        // A successful screen fallback also replaces the old QuestCraft
        // keyboard. Do not suppress the native fallback if we drew nothing.
        if (!detached) QuestCraftKeyboardSuppressor.closeWhenScreenFallbackVisible();
    }

    /**
     * Alpha-coverage rounded rectangle without Minecraft's blocky cut-out
     * corners. All fill operations use the stable DrawContext API, so this
     * also works on QuestCraft's Android OpenGL compatibility renderer.
     *
     * Curved pixels blend with the backdrop at fractional coverage instead
     * of snapping each arc edge to a solid one-pixel staircase.
     */
    /** Non-fox seasonal decor, deliberately confined to the panel corners. */
    private static void drawSnowDots(DrawContext c, KeyboardLayout.Panel p, long now) {
        int twinkle = (int) ((now / 430) % 3);
        for (int i = 0; i < 9; i++) {
            int x = i < 5 ? p.x() + 12 + i * 9
                : p.x() + p.width() - 12 - (i - 5) * 9;
            int y = i < 5 ? p.y() + 7 + (i % 3) * 4
                : p.y() + p.height() - 10 - (i % 3) * 4;
            int alpha = ((i + twinkle) % 3 == 0) ? 0xD0 : 0x80;
            c.fill(x, y, x + 2, y + 2, (alpha << 24) | 0x00F5FFFF);
        }
    }

    /** Quest-style small white circular contact point with a faint cool glow. */
    private static void drawAimCircle(DrawContext context, int x, int y) {
        // Anti-aliased-looking fringe within a small 7px footprint.
        context.fill(x - 2, y - 2, x + 3, y + 3, 0x448CD9FF);
        context.fill(x - 2, y - 1, x + 3, y + 2, 0xC6DDF7FF);
        context.fill(x - 1, y - 2, x + 2, y + 3, 0xC6DDF7FF);
        context.fill(x - 1, y - 1, x + 2, y + 2, 0xFFFFFFFF);
    }

    private static void rounded(DrawContext c, int x, int y, int w, int h,
                                int radius, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
        int rad = Math.min(radius, Math.min(w, h) / 2);
        if (rad <= 0) {
            c.fill(x, y, x + w, y + h, color);
            return;
        }
        c.fill(x + rad, y, x + w - rad, y + h, color);
        if (h > rad * 2) c.fill(x, y + rad, x + w, y + h - rad, color);

        // Four-sample vertical coverage on each corner pixel. We render
        // opaque interior rows and independently blended fractional rims.
        int alpha = (color >>> 24) & 255;
        int rgb = color & 0x00FFFFFF;
        for (int row = 0; row < rad; row++) {
            for (int dx = 0; dx < rad; dx++) {
                double covered = 0.0;
                for (int sub = 0; sub < 4; sub++) {
                    double yy = rad - row - (sub + 0.5) * 0.25;
                    double xx = rad - dx - 0.5;
                    if (xx * xx + yy * yy <= rad * rad) covered += 0.25;
                }
                if (covered <= 0.0) continue;
                int aa = (int) Math.round(alpha * covered);
                int shade = (Math.max(1, aa) << 24) | rgb;
                c.fill(x + dx, y + row, x + dx + 1, y + row + 1, shade);
                c.fill(x + w - dx - 1, y + row,
                    x + w - dx, y + row + 1, shade);
                c.fill(x + dx, y + h - row - 1,
                    x + dx + 1, y + h - row, shade);
                c.fill(x + w - dx - 1, y + h - row - 1,
                    x + w - dx, y + h - row, shade);
            }
        }
    }

    private static void drawChecker(DrawContext c, int x, int y) {
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 3 - row; col++)
                if (((row + col) & 1) == 0)
                    c.fill(x + col * 3, y + row * 3,
                        x + col * 3 + 3, y + row * 3 + 3, 0xAAE77720);
    }

    /** Tiny pixel fox crest, as in the visual reference; no external texture. */
    private static void drawFoxBadge(DrawContext c, int x, int y) {
        String[] pixels = {
            "O....O", "OO..OO", "OOOOOO", "OWOOWO",
            "OWKKWO", "OWWWWO", ".OWWO."
        };
        for (int py = 0; py < pixels.length; py++) {
            for (int px = 0; px < pixels[py].length(); px++) {
                int color = switch (pixels[py].charAt(px)) {
                    case 'O' -> 0xFFF47A19;
                    case 'W' -> 0xFFFFE6C2;
                    case 'K' -> 0xFF272129;
                    default -> 0;
                };
                if (color != 0)
                    c.fill(x + px * 2, y + py * 2,
                        x + px * 2 + 2, y + py * 2 + 2, color);
            }
        }
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
        // Controller state can be ready a frame before the detached layout.
        if (panel == null) return;
        if (focusedField(screen) != null)
            handPointer(hand).press(panel, x, y,
                System.nanoTime() / 1_000_000L, focusedField(screen));
    }

    public void vrMoved(Screen screen, double x, double y, int hand) {
        if (panel != null)
            handPointer(hand).move(panel, x, y, focusedField(screen));
    }

    public void vrReleased(Screen screen, double x, double y, int hand) {
        if (panel == null) {
            handPointer(hand).cancel();
            return;
        }
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
        // The click is inspected BEFORE Minecraft changes the focused widget.
        // Ignore auto-focus; clicking outside the field/panel hides the
        // keyboard and returns the world list and menu buttons to normal.
        if (!(screen instanceof ChatScreen)) {
            TextFieldWidget clickedField = textFieldAt(screen, x, y);
            if (clickedField != null) {
                activatedMenuScreen = screen;
                activatedMenuField = clickedField;
            } else if (screen != previousScreen || panel == null ||
                       !panel.contains(x, y)) {
                activatedMenuScreen = null;
                activatedMenuField = null;
                if (!FoxCraftVrPlane.isReady()) {
                    panel = null;
                    pointer.cancel();
                    leftPointer.cancel();
                }
            }
        }
        // When the detached panel is unavailable, the visible fallback
        // must also accept mouse/controller presses instead of discarding them.
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
        // When the detached panel is unavailable, the visible fallback
        // must also accept mouse/controller presses instead of discarding them.
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
            case LANGUAGE_RU -> { russian = true; symbols = false; }
            case LANGUAGE_EN -> { russian = false; symbols = false; }
            case SYMBOLS -> symbols = !symbols;
            case RESIZE -> { /* Fixed size; intentionally disabled. */ }
            case SETTINGS -> { settingsOpen = true; panel = null; }
            case BACK_TO_KEYS -> { settingsOpen = false; panel = null; }
            case THEME -> {
                theme = KeyboardTheme.fromName(result.key().value());
                // Keep the settings page open so another theme can be previewed.
                panel = null;
            }
            case SHIFT -> shift = !shift;
            case COLLAPSE -> { collapsed = true; settingsOpen = false; }
            case EXPAND -> collapsed = false;
            default -> { }
        }
        if (result.key().command() == KeyboardLayout.Command.TYPE && shift)
            shift = false;
    }

    /** Select only a visible input field directly clicked by the user. */
    private static TextFieldWidget textFieldAt(Screen screen, double x, double y) {
        if (screen == null) return null;
        for (Element child : screen.children()) {
            if (child instanceof TextFieldWidget field && field.isVisible()
                && x >= field.getX() && x < field.getX() + field.getWidth()
                && y >= field.getY() && y < field.getY() + field.getHeight())
                return field;
        }
        return null;
    }

    /** Do not open a VR keyboard unless a real, visible Minecraft text field has focus. */
    public static boolean hasEditableFocus(Screen screen) {
        return focusedField(screen) != null;
    }

    private static TextFieldWidget focusedField(Screen screen) {
        if (screen == null) return null;
        Element focused = screen.getFocused();
        // Only handle editable, focused fields. Never steal player movement or
        // send text to chat when the player is not actually editing.
        if (focused instanceof TextFieldWidget field && field.isVisible())
            return field;
        // Some QuestCraft menu screens focus their text widget directly
        // without setting Screen.getFocused(). Recover only a genuinely
        // focused, visible child; never select an arbitrary search box.
        for (Element child : screen.children()) {
            if (child instanceof TextFieldWidget field &&
                field.isVisible() && field.isFocused()) return field;
        }
        return null;
    }
}

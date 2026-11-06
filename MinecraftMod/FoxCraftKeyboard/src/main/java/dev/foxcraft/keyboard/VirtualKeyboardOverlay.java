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
    private String lastInsertedSuffix = "";
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
    private String menuPage = "KEYBOARD";
    private String layoutMenuPage = "";
    private String voiceStatus = "";
    private String localClipboard = "";
    private final QuickMessageDraft quickDraft = new QuickMessageDraft();
    private final QuickBindingStore quickBinds = new QuickBindingStore(
        FabricLoader.getInstance().getConfigDir().resolve("foxcraft-keyboard-binds.properties"));
    private int quickPage;
    private int editingQuickSlot = -1;
    private int deleteQuickSlot = -1;
    private long deleteQuickTime;
    private long voiceStatusAt;
    private final FoxCraftFeatureSettings featurePrefs =
        new FoxCraftFeatureSettings(FabricLoader.getInstance().getConfigDir()
            .resolve("foxcraft-vr-features.properties"));

    public FoxCraftFeatureSettings features() { return featurePrefs; }
    private final KeyboardThemePreferences themePrefs =
        new KeyboardThemePreferences(FabricLoader.getInstance().getConfigDir()
            .resolve("foxcraft-keyboard-theme.txt"));
    private KeyboardTheme theme = themePrefs.load();
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
        FoxCraftVoiceInput.poll();
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
            menuPage = "KEYBOARD";
            quickDraft.clear();
            editingQuickSlot = -1;
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
            layoutSettingsOpen != settingsOpen ||
            !layoutMenuPage.equals(menuPage)) {
            panel = KeyboardLayout.make(width, height,
                chat && !detached, russian, symbols, shift, collapsed,
                preferredWidth, suggestions, chatInputBottomY, detached);
            if (settingsOpen && panel != null) {
                panel = switch (menuPage) {
                    case "THEMES" -> KeyboardLayout.themeMenu(panel, theme.name());
                    case "SETTINGS" -> KeyboardLayout.toolMenu(panel,
                        new String[]{"ТЕМЫ", "МИКРОФОН", "Бинды",
                            "ПЕРЕМЕЩЕНИЕ", "КОНТРОЛЛЕРЫ", "← К клавиатуре"},
                        new KeyboardLayout.Command[]{KeyboardLayout.Command.THEMES_PAGE,
                            KeyboardLayout.Command.MICROPHONE,
                            KeyboardLayout.Command.QUICK_MESSAGES,
                            KeyboardLayout.Command.MOVE_MENU,
                            KeyboardLayout.Command.CONTROLLER_MENU,
                            KeyboardLayout.Command.BACK_TO_KEYS},
                        new String[]{"", "", "", "", "", ""});
                    case "QUICK" -> KeyboardLayout.quickBindingsMenu(panel,
                        quickBinds.pageLabels(Math.min(quickPage, quickBinds.pages() - 1)),
                        Math.min(quickPage, quickBinds.pages() - 1),
                        quickBinds.pages());
                    case "COMPOSE" -> KeyboardLayout.composeMenu(panel,
                        quickDraft.message(), quickDraft.note(),
                        quickDraft.editingNote(), russian, shift,
                        editingQuickSlot >= 0);
                    case "CLIPBOARD" -> KeyboardLayout.toolMenu(panel,
                        new String[]{"КОПИРОВАТЬ", "ВСТАВИТЬ",
                            "ВЫДЕЛИТЬ ВСЁ", "← Назад"},
                        new KeyboardLayout.Command[]{KeyboardLayout.Command.COPY,
                            KeyboardLayout.Command.PASTE,
                            KeyboardLayout.Command.SELECT_ALL,
                            KeyboardLayout.Command.BACK_TO_KEYS},
                        new String[]{"", "", "", ""});
                    case "MOVE" -> KeyboardLayout.toolMenu(panel,
                        new String[]{"ЗАЖМИ И ПЕРЕТАЩИ", "← Левее",
                            "Правее →", "↑ Выше", "↓ Ниже",
                            "БЛИЖЕ", "ДАЛЬШЕ", "СБРОС", "← Назад"},
                        new KeyboardLayout.Command[]{KeyboardLayout.Command.MOVE_GRAB,
                            KeyboardLayout.Command.MOVE_STEP, KeyboardLayout.Command.MOVE_STEP,
                            KeyboardLayout.Command.MOVE_STEP, KeyboardLayout.Command.MOVE_STEP,
                            KeyboardLayout.Command.MOVE_STEP, KeyboardLayout.Command.MOVE_STEP,
                            KeyboardLayout.Command.MOVE_RESET,
                            KeyboardLayout.Command.SETTINGS_HOME},
                        new String[]{"", "LEFT", "RIGHT", "UP", "DOWN",
                            "NEAR", "FAR", "", ""});
                    case "CONTROLLERS" -> KeyboardLayout.toolMenu(panel,
                        new String[]{featurePrefs.haptics() ? "ВИБРАЦИЯ: ВКЛ" : "ВИБРАЦИЯ: ВЫКЛ",
                            "СИЛА: " + featurePrefs.hapticStrength(),
                            featurePrefs.leftHand() ? "ЛЕВАЯ: ВКЛ" : "ЛЕВАЯ: ВЫКЛ",
                            featurePrefs.rightHand() ? "ПРАВАЯ: ВКЛ" : "ПРАВАЯ: ВЫКЛ",
                            "ЛУЧ КОРОЧЕ", "ЛУЧ ДЛИННЕЕ",
                            "ТОЧКА МЕНЬШЕ", "ТОЧКА БОЛЬШЕ", "← Назад"},
                        new KeyboardLayout.Command[]{KeyboardLayout.Command.HAPTIC_TOGGLE,
                            KeyboardLayout.Command.HAPTIC_STRENGTH,
                            KeyboardLayout.Command.CONTROLLER_LEFT_TOGGLE,
                            KeyboardLayout.Command.CONTROLLER_RIGHT_TOGGLE,
                            KeyboardLayout.Command.BEAM_SHORTER,
                            KeyboardLayout.Command.BEAM_LONGER,
                            KeyboardLayout.Command.DOT_SMALLER,
                            KeyboardLayout.Command.DOT_LARGER,
                            KeyboardLayout.Command.SETTINGS_HOME},
                        new String[]{"", "", "", "", "", "", "", "", ""});
                    default -> panel;
                };
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
            layoutMenuPage = menuPage;
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
        // New Year's theme uses a RED chrome/outline, independent of its
        // golden tree star and other palette accents.
        int chrome = theme == KeyboardTheme.NEW_YEAR ? 0xFFE23B50 : accent;
        int border = chrome;

        // HD-inspired multilayer chassis: diffuse light, cool top rim,
        // smooth alpha-covered corners and a restrained charcoal gradient.
        for (int layer = 6; layer >= 1; layer--) {
            int alpha = Math.max(1, (20 - layer * 2));
            rounded(context, panel.x() - layer, panel.y() - layer,
                panel.width() + layer * 2, panel.height() + layer * 2,
                14 + layer, (alpha << 24) | (chrome & 0x00FFFFFF));
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
            panel.x() + panel.width() - 21, panel.y() - 1, ((chrome & 0x00FFFFFF) | 0x66000000));
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

        // Animate only the chassis beneath and BETWEEN the keycaps.
        // Draw before every key: even partial overlaps cannot cover labels,
        // change hover states or intercept controller hit testing.
        drawThemeAmbient(context, panel, theme, now);

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
                // Alternative themes display the user's requested animated
                // illustration INSTEAD of a plain theme name. Classic logo
                // stays untouched; this crest does not receive input events.
                KeyboardHeaderArt.render(context, panel, theme, now);
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
            boolean composeSelected = "COMPOSE".equals(menuPage) &&
                ((key.command() == KeyboardLayout.Command.COMPOSE_NOTE &&
                    quickDraft.editingNote()) ||
                 (key.command() == KeyboardLayout.Command.COMPOSE_MESSAGE &&
                    !quickDraft.editingNote()));
            if (composeSelected && !hovered && !longHeld) bg = keyHover;
            if (key.command() == KeyboardLayout.Command.COMPOSE_SEND &&
                !hovered && !longHeld) bg = theme.enter();
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
                    key.width() + 4, key.height() + 4, 7, (0x55000000 | (chrome & 0x00FFFFFF)));
                rounded(context, key.x() - 1, key.y() - 1,
                    key.width() + 2, key.height() + 2, 6, chrome);
            } else {
                rounded(context, key.x(), key.y(),
                    key.width(), key.height(), 6, (theme == KeyboardTheme.CLASSIC ? 0xFF494553 : chrome));
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

            // Alternate legends are completely INSIDE the rounded keycap.
            // Small Russian keys need a 5px inset so even the antialiased
            // glyph and its shadow cannot touch or cross the orange border.
            int labelInset = 5;
            if (!key.alternate().isEmpty() && key.height() >= 18) {
                int glyphWidth = font.getWidth(key.alternate());
                if (glyphWidth <= key.width() - 2 * labelInset) {
                    context.drawText(font, key.alternate(),
                        key.x() + key.width() - glyphWidth - labelInset,
                        key.y() + 3, legendColor, false);
                }
            }

            if (drawControlIcon(context, key, textColor, accent)) continue;
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
            // All labels obey a safe inner box, not merely the outer key
            // rectangle. Avoid text extending into neighbouring borders.
            int maxCaptionWidth = Math.max(3, key.width() - 2 * labelInset);
            if (font.getWidth(caption) > maxCaptionWidth) {
                String fullCaption = caption;
                while (caption.length() > 1 &&
                    font.getWidth(caption) > maxCaptionWidth)
                    caption = caption.substring(0, caption.length() - 1);
                if (!caption.equals(fullCaption) && caption.length() > 1 &&
                    font.getWidth(caption + "…") <= maxCaptionWidth)
                    caption += "…";
            }
            int captionWidth = font.getWidth(caption);
            int tx = key.x() + Math.max(labelInset,
                (key.width() - captionWidth) / 2);
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
            featurePrefs.leftHand() &&
            panel.contains(leftSample.x(), leftSample.y())) {
            int lx = (int) leftSample.x();
            int ly = (int) leftSample.y();
            // Compact circular marker at the actual left-hand hit point.
            drawAimCircle(context, lx, ly);
        }

        // The right controller is the primary GUI pointer. In detached VR
        // mode its position is projected independently of the left marker.
        // Draw both so missing native QuestCraft rays are immediately obvious.
        if (panel.contains(mouseX, mouseY) && featurePrefs.rightHand()) {
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

        // Brief, truthful microphone/clipboard feedback; never a text input.
        if (!voiceStatus.isEmpty() && now - voiceStatusAt < 4100) {
            String shown = voiceStatus;
            while (shown.length() > 1 &&
                font.getWidth(shown) > Math.max(40, panel.width() - 20))
                shown = shown.substring(0, shown.length() - 1);
            int sx = panel.x() + (panel.width() - font.getWidth(shown)) / 2;
            context.fill(sx - 5, panel.y() + 3,
                sx + font.getWidth(shown) + 5, panel.y() + 16, keyBg);
            context.drawText(font, shown, sx, panel.y() + 5, accent, false);
        }

        // Light stays on the chassis edge; no moving bar crossing the logo.
        int glow = (int) (30 + 20 * (1 + Math.sin(now / 500.0)));
        context.fill(panel.x() + 12, panel.y() + panel.height() - 2,
            panel.x() + panel.width() - 12, panel.y() + panel.height() - 1,
            (Math.min(255, glow) << 24) | (chrome & 0x00FFFFFF));
        // A successful screen fallback also replaces the old QuestCraft
        // keyboard. Do not suppress the native fallback if we drew nothing.
        if (!detached) QuestCraftKeyboardSuppressor.closeWhenScreenFallbackVisible();
    }

    /** Pixel-drawn VR pictograms, avoiding missing emoji glyphs on Quest. */
    private static boolean drawControlIcon(DrawContext c, KeyboardLayout.Key key,
                                           int ink, int accent) {
        KeyboardLayout.Command cmd = key.command();
        if (cmd != KeyboardLayout.Command.MICROPHONE &&
            cmd != KeyboardLayout.Command.QUICK_MESSAGES &&
            cmd != KeyboardLayout.Command.CLIPBOARD &&
            cmd != KeyboardLayout.Command.ESCAPE &&
            cmd != KeyboardLayout.Command.QUICK_EDIT &&
            cmd != KeyboardLayout.Command.QUICK_DELETE) return false;
        int cx = key.x() + key.width() / 2;
        int cy = key.y() + key.height() / 2;
        if (cmd == KeyboardLayout.Command.MICROPHONE) {
            // Capsule, cradle and stand.
            rounded(c, cx - 3, cy - 6, 6, 9, 3, ink);
            c.fill(cx - 6, cy - 1, cx - 4, cy + 3, ink);
            c.fill(cx + 4, cy - 1, cx + 6, cy + 3, ink);
            c.fill(cx - 5, cy + 3, cx + 5, cy + 5, ink);
            c.fill(cx - 1, cy + 5, cx + 1, cy + 8, ink);
        } else if (cmd == KeyboardLayout.Command.QUICK_MESSAGES) {
            // Message card + three short lines: quick-bind pictogram.
            rounded(c, cx - 8, cy - 6, 16, 12, 2, ink);
            c.fill(cx - 5, cy - 3, cx + 5, cy - 2, accent);
            c.fill(cx - 5, cy, cx + 3, cy + 1, accent);
            c.fill(cx - 5, cy + 3, cx + 1, cy + 4, accent);
        } else if (cmd == KeyboardLayout.Command.CLIPBOARD) {
            rounded(c, cx - 6, cy - 6, 12, 14, 2, ink);
            rounded(c, cx - 3, cy - 8, 6, 4, 1, accent);
            c.fill(cx - 3, cy - 1, cx + 3, cy, accent);
            c.fill(cx - 3, cy + 3, cx + 3, cy + 4, accent);
        } else if (cmd == KeyboardLayout.Command.QUICK_DELETE) {
            // Small corner deletion mark, deliberately separate from gear.
            c.fill(cx - 4, cy - 4, cx - 2, cy - 2, ink);
            c.fill(cx - 2, cy - 2, cx + 2, cy + 2, ink);
            c.fill(cx + 2, cy + 2, cx + 4, cy + 4, ink);
            c.fill(cx + 2, cy - 4, cx + 4, cy - 2, accent);
            c.fill(cx - 2, cy - 2, cx + 2, cy + 2, accent);
            c.fill(cx - 4, cy + 2, cx - 2, cy + 4, accent);
        } else if (cmd == KeyboardLayout.Command.ESCAPE) {
            // Door and outward arrow (bottom-left exit).
            c.fill(cx - 6, cy - 7, cx - 4, cy + 7, ink);
            c.fill(cx - 6, cy - 7, cx + 2, cy - 5, ink);
            c.fill(cx - 6, cy + 5, cx + 2, cy + 7, ink);
            c.fill(cx - 1, cy - 1, cx + 7, cy + 1, ink);
            c.fill(cx + 4, cy - 4, cx + 6, cy + 4, ink);
        } else {
            // Inset settings gear: distinct from tapping the message itself.
            rounded(c, cx - 5, cy - 5, 10, 10, 3, ink);
            c.fill(cx - 2, cy - 2, cx + 2, cy + 2, accent);
            c.fill(cx - 1, cy - 7, cx + 1, cy - 5, ink);
            c.fill(cx - 1, cy + 5, cx + 1, cy + 7, ink);
            c.fill(cx - 7, cy - 1, cx - 5, cy + 1, ink);
            c.fill(cx + 5, cy - 1, cx + 7, cy + 1, ink);
        }
        return true;
    }

    /**
     * Alpha-coverage rounded rectangle without Minecraft's blocky cut-out
     * corners. All fill operations use the stable DrawContext API, so this
     * also works on QuestCraft's Android OpenGL compatibility renderer.
     *
     * Curved pixels blend with the backdrop at fractional coverage instead
     * of snapping each arc edge to a solid one-pixel staircase.
     */
    /**
     * Ambient moving lights for every theme. Geometry stays behind the keys
     * and small particles are clipped to gutters and the panel background.
     * This is paint only; the entire hit-test/key layout is untouched.
     */
    private static void drawThemeAmbient(DrawContext c, KeyboardLayout.Panel p,
                                         KeyboardTheme theme, long now) {
        int width = p.width();
        int height = p.height();
        if (width <= 20 || height <= 20) return;
        int rgb = theme.accent() & 0x00FFFFFF;
        long tick = now / (23 + theme.ordinal() * 5L);

        // Gentle animated light travelling along both side rails.
        for (int side = 0; side < 2; side++) {
            int x = side == 0 ? p.x() + 3 : p.x() + width - 4;
            int phase = (int) Math.floorMod(tick + side * (height / 2L), height - 12);
            int y = p.y() + 6 + phase;
            c.fill(x, y, x + 1, y + 5, 0xA0000000 | rgb);
            c.fill(x, y - 2, x + 1, y, 0x44000000 | rgb);
            c.fill(x, y + 5, x + 1, y + 7, 0x44000000 | rgb);
        }

        // Flowing one-pixel highlights in the gaps directly under key rows.
        // Each key itself is drawn AFTER this pass, completely opaque.
        int row = 0;
        int lastY = Integer.MIN_VALUE;
        for (KeyboardLayout.Key k : p.keys()) {
            if (k.y() == lastY) continue;
            lastY = k.y();
            int gutterY = k.y() + k.height();
            if (gutterY >= p.y() + height - 3) continue;
            int x = p.x() + 8 + (int) Math.floorMod(tick + row * 71L,
                Math.max(1, width - 16));
            if (clearAmbientPixel(p, x, gutterY)) {
                c.fill(x, gutterY, x + 2, gutterY + 1,
                    0x9A000000 | rgb);
            }
            row++;
        }

        // Each palette has a different motion:
        // embers rise, stars twinkle, bubbles rise, petals descend,
        // aurora glides sideways, and New Year snow falls.
        int number = theme == KeyboardTheme.NEW_YEAR ? 38 : 25;
        for (int i = 0; i < number; i++) {
            long t = now / (theme == KeyboardTheme.SAKURA ? 49L : 39L);
            long seed = i * 97L + theme.ordinal() * 53L;
            int localX = 6 + (int) Math.floorMod(seed, width - 12);
            int localY = 6 + (int) Math.floorMod(seed * 3L, height - 12);
            int light = 0x78000000 | rgb;
            switch (theme) {
                case CLASSIC -> {
                    localX = 6 + (int) Math.floorMod(seed + (t / 6), width - 12);
                    localY = 6 + (int) Math.floorMod(seed * 3L - t, height - 12);
                }
                case MIDNIGHT -> {
                    light = (((now / 240L + i) % 4 == 0 ? 0xCC : 0x55) << 24)
                        | 0x00E7EAFF;
                }
                case OCEAN -> {
                    localY = 6 + (int) Math.floorMod(seed * 3L - t, height - 12);
                    light = 0x7A000000 | 0x0097FFFF;
                }
                case SAKURA -> {
                    localX = 6 + (int) Math.floorMod(seed + t / 3, width - 12);
                    localY = 6 + (int) Math.floorMod(seed * 3L + t, height - 12);
                    light = 0x98000000 | 0x00FFD4EB;
                }
                case AURORA -> {
                    localX = 6 + (int) Math.floorMod(seed + t * 2L, width - 12);
                    localY = 6 + (int) Math.floorMod(seed * 3L +
                        (long) (7 * Math.sin(now / 500.0 + i)), height - 12);
                    light = 0x81000000 | 0x0085FFD5;
                }
                case NEW_YEAR -> {
                    localX = 6 + (int) Math.floorMod(seed + t / 8, width - 12);
                    localY = 6 + (int) Math.floorMod(seed * 3L + t, height - 12);
                    light = 0xB5000000 | 0x00F0FCFF;
                }
            }
            int x = p.x() + localX, y = p.y() + localY;
            // Never draw over keycaps, captions, or controller hit markers.
            if (clearAmbientPixel(p, x, y)) {
                c.fill(x, y, x + 1, y + 1, light);
            }
        }
    }

    private static boolean clearAmbientPixel(KeyboardLayout.Panel p, int x, int y) {
        if (x < p.x() + 2 || x >= p.x() + p.width() - 2 ||
            y < p.y() + 2 || y >= p.y() + p.height() - 2) return false;
        for (KeyboardLayout.Key key : p.keys()) {
            if (key.contains(x, y)) return false;
        }
        return true;
    }

    /**
     * Actual FALLING snowflakes, not the old stationary decoration dots.
     * All pixels remain inside the chassis and are painted before keycaps,
     * so falling snow can never obscure a letter or an active hit marker.
     */
    private static void drawSnowDots(DrawContext c, KeyboardLayout.Panel p, long now) {
        int w = p.width() - 14;
        int h = p.height() - 14;
        if (w < 10 || h < 10) return;
        for (int i = 0; i < 28; i++) {
            int speed = 80 + (i % 4) * 31;
            int x = p.x() + 7 + (int) Math.floorMod(
                i * 83L + (long) (5 * Math.sin(now / 750.0 + i)), w);
            int y = p.y() + 7 + (int) Math.floorMod(
                i * 37L + now / speed, h);
            int color = (i % 4 == 0 ? 0xD0FFFFFF : 0x99DEF4FF);
            // Six-spoke snowflake silhouette, including diagonal branches.
            snowPixel(c, p, x, y, color);
            for (int d = 1; d <= 3; d++) {
                snowPixel(c, p, x + d, y, color);
                snowPixel(c, p, x - d, y, color);
                snowPixel(c, p, x, y + d, color);
                snowPixel(c, p, x, y - d, color);
            }
            for (int direction : new int[]{-1, 1}) {
                snowPixel(c, p, x + direction * 2, y - 1, color);
                snowPixel(c, p, x + direction * 2, y + 1, color);
                snowPixel(c, p, x - 1, y + direction * 2, color);
                snowPixel(c, p, x + 1, y + direction * 2, color);
            }
        }
    }

    private static void snowPixel(DrawContext c, KeyboardLayout.Panel p,
                                  int x, int y, int color) {
        if (clearAmbientPixel(p, x, y)) c.fill(x, y, x + 1, y + 1, color);
    }

    /** Quest-style small white circular contact point with a faint cool glow. */
    private void drawAimCircle(DrawContext context, int x, int y) {
        int radius = featurePrefs.dotRadius();
        // Small white circular spot with a cool-blue translucent outline.
        for (int dy = -radius - 1; dy <= radius + 1; dy++) {
            int outer = (int) Math.floor(Math.sqrt(Math.max(0,
                (radius + 1) * (radius + 1) - dy * dy)));
            int inner = (int) Math.floor(Math.sqrt(Math.max(0,
                radius * radius - dy * dy)));
            context.fill(x - outer, y + dy, x + outer + 1, y + dy + 1,
                0x668CD9FF);
            if (Math.abs(dy) <= radius)
                context.fill(x - inner, y + dy, x + inner + 1, y + dy + 1,
                    0xFFF8FCFF);
        }
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
            if (release.activation() != null && field != null) {
                FoxCraftHaptics.pulse(1, featurePrefs.hapticStrength());
                applyRelease(screen, release);
            }
        }
        leftDown = pressed;
    }

    /** VR-plane controller input; separate state machines for each hand. */
    public boolean vrPressed(Screen screen, double x, double y, int hand) {
        // The first controller event may arrive before this frame's detached
        // keyboard is painted. Let the poller retry instead of latching an
        // unhandled trigger forever until the user releases it.
        if (panel == null || !layoutDetached || !shouldDisplayForScreen(screen))
            return false;
        TextFieldWidget field = focusedField(screen);
        if (field == null) return false;
        boolean accepted = handPointer(hand).press(panel, x, y,
            System.nanoTime() / 1_000_000L, field);
        if (accepted && "MOVE".equals(menuPage)) {
            KeyboardLayout.Key key = panel.at(x, y);
            if (key != null && key.command() == KeyboardLayout.Command.MOVE_GRAB)
                FoxCraftVrPlane.beginGrab(hand);
        }
        return accepted;
    }

    public void vrMoved(Screen screen, double x, double y, int hand) {
        if (panel != null)
            handPointer(hand).move(panel, x, y, focusedField(screen));
    }

    public void vrReleased(Screen screen, double x, double y, int hand) {
        FoxCraftVrPlane.finishGrab(hand);
        if (panel == null) {
            handPointer(hand).cancel();
            return;
        }
        KeyboardPointerInput.Release release = handPointer(hand).release(
            panel, x, y, System.nanoTime() / 1_000_000L, focusedField(screen));
        if (release.activation() != null && focusedField(screen) != null) {
            FoxCraftHaptics.pulse(hand, featurePrefs.hapticStrength());
            applyRelease(screen, release);
        }
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
        if (release.activation() != null && focusedField(screen) != null) {
            if (FoxCraftVrPlane.isVrSession())
                FoxCraftHaptics.pulse(0, featurePrefs.hapticStrength());
            applyRelease(screen, release);
        }
        return release.consumed();
    }

    /** Swipe candidates are bound to the exact inserted word and cursor. */
    private void applyRelease(Screen screen, KeyboardPointerInput.Release release) {
        KeyHoldController.Activation result = release.activation();
        TextFieldWidget field = focusedField(screen);
        if (result == null || field == null) return;
        if ("COMPOSE".equals(menuPage) &&
            result.key().command() == KeyboardLayout.Command.TYPE &&
            !release.suggestions().isEmpty()) {
            // Swipes edit the selected local draft field, never the live chat.
            String word = KeyboardLayout.swipeWord(result.text(), russian, shift,
                quickDraft.atStartOfLine());
            quickDraft.insert(word);
            shift = false;
            panel = null;
            return;
        }
        if (result.key().command() == KeyboardLayout.Command.TYPE &&
            !release.suggestions().isEmpty()) {
            clearSuggestions();
            // Capitalize only at the start of the input line. Swipe never
            // inserts an automatic space after every word.
            int before = field.getCursor();
            boolean atStartOfLine = before == 0 ||
                field.getText().substring(0, Math.min(before, field.getText().length()))
                    .isBlank();
            String chosen = KeyboardLayout.swipeWord(result.text(), russian, shift,
                atStartOfLine);
            List<String> choices = release.suggestions().stream()
                .map(word -> KeyboardLayout.swipeWord(word, russian, shift,
                    atStartOfLine)).toList();
            shift = false;
            // Explicit Space key controls word separation. No automatic
            // spaces after swipe words, including candidate replacements.
            String suffix = "";
            String inserted = chosen;
            field.write(inserted);
            int after = field.getCursor();
            String actual = field.getText();
            if (after - before == inserted.length() &&
                after <= actual.length() &&
                actual.substring(before, after).equals(inserted)) {
                suggestions = choices;
                suggestionField = field;
                suggestionSnapshot = actual;
                suggestionCursor = after;
                lastInsertedWord = chosen;
                lastInsertedSuffix = suffix;
                panel = null;
            }
        } else activate(screen, result);
    }

    private void clearSuggestions() {
        if (!suggestions.isEmpty()) panel = null;
        suggestions = List.of();
        suggestionField = null;
        suggestionSnapshot = "";
        suggestionCursor = -1;
        lastInsertedWord = "";
        lastInsertedSuffix = "";
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
                suggestionCursor >= lastInsertedWord.length() + lastInsertedSuffix.length() &&
                field.getText().substring(suggestionCursor -
                    lastInsertedWord.length() - lastInsertedSuffix.length(),
                    suggestionCursor).equals(lastInsertedWord + lastInsertedSuffix) &&
                suggestions.contains(candidate)) {
                field.eraseCharacters(-(lastInsertedWord.length() + lastInsertedSuffix.length()));
                field.write(candidate + lastInsertedSuffix);
            }
            clearSuggestions();
            return;
        }
        clearSuggestions();
        if ("COMPOSE".equals(menuPage)) {
            activateCompose(screen, field, result);
            return;
        }
        switch (result.key().command()) {
            case TYPE -> {
                if (".".equals(result.text())) {
                    // A swipe appends a space. A following period belongs
                    // immediately after that word, followed by exactly one
                    // new space: "Привет. ", not "Привет .  ".
                    if (KeyboardLayout.trimSpaceBeforeDot(field.getText(),
                        field.getCursor()))
                        field.eraseCharacters(-1);
                    field.write("." + KeyboardLayout.trailingWordSpace(
                        field.getText(), field.getCursor()));
                } else {
                    field.write(result.text());
                }
            }
            case SPACE -> field.write(" ");
            case HISTORY_PREVIOUS -> {
                if (screen instanceof ChatScreen)
                    screen.keyPressed(GLFW.GLFW_KEY_UP, 0, 0);
            }
            case HISTORY_NEXT -> {
                if (screen instanceof ChatScreen)
                    screen.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0);
            }
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
            case SETTINGS -> openPage("SETTINGS");
            case THEMES_PAGE -> openPage("THEMES");
            case SETTINGS_HOME -> openPage("SETTINGS");
            case BACK_TO_KEYS -> openPage("KEYBOARD");
            case QUICK_MESSAGES -> openPage("QUICK");
            case QUICK_PAGE_PREVIOUS -> {
                quickPage = (quickPage + quickBinds.pages() - 1) %
                    quickBinds.pages();
                deleteQuickSlot = -1;
                openPage("QUICK");
            }
            case QUICK_PAGE_NEXT -> {
                quickPage = (quickPage + 1) % quickBinds.pages();
                deleteQuickSlot = -1;
                openPage("QUICK");
            }
            case QUICK_ADD -> {
                int newSlot = quickBinds.add();
                if (newSlot == -1) {
                    showStatus("Достигнут предел биндов");
                } else {
                    quickPage = newSlot / QuickBindingStore.PAGE_SIZE;
                    editingQuickSlot = newSlot;
                    quickDraft.clear();
                    deleteQuickSlot = -1;
                    openPage("COMPOSE");
                }
            }
            case QUICK_DELETE -> {
                int slot = Integer.parseInt(result.key().value());
                long nowMs = System.nanoTime() / 1_000_000L;
                if (slot != deleteQuickSlot ||
                    nowMs - deleteQuickTime > 5000) {
                    deleteQuickSlot = slot;
                    deleteQuickTime = nowMs;
                    showStatus("Нажми крестик повторно для удаления");
                } else {
                    quickBinds.remove(slot);
                    quickPage = Math.min(quickPage, quickBinds.pages() - 1);
                    deleteQuickSlot = -1;
                    showStatus("Бинд удалён");
                    openPage("QUICK");
                }
            }
            case QUICK_EDIT -> {
                deleteQuickSlot = -1;
                editingQuickSlot = Integer.parseInt(result.key().value());
                quickDraft.load(quickBinds.message(editingQuickSlot),
                    quickBinds.note(editingQuickSlot));
                openPage("COMPOSE");
            }
            case QUICK_COMPOSE -> {
                editingQuickSlot = -1;
                quickDraft.clear();
                openPage("COMPOSE");
            }
            case CLIPBOARD -> openPage("CLIPBOARD");
            case MOVE_MENU -> openPage("MOVE");
            case CONTROLLER_MENU -> openPage("CONTROLLERS");
            case QUICK_MESSAGE -> {
                int slot = Integer.parseInt(result.key().value());
                String preset = quickBinds.outgoing(slot);
                if (preset.isEmpty()) showStatus("Пустой бинд: нажми шестерёнку");
                else {
                    field.write(preset);
                    openPage("KEYBOARD");
                }
            }
            case MICROPHONE -> startDictation(screen, field);
            case COPY -> {
                String selection = field.getSelectedText();
                if (selection.isEmpty()) showStatus("Сначала выдели текст");
                else {
                    localClipboard = selection;
                    try {
                        GLFW.glfwSetClipboardString(
                            MinecraftClient.getInstance().getWindow().getHandle(),
                            selection);
                        showStatus("Текст скопирован");
                    } catch (RuntimeException | LinkageError e) {
                        showStatus("Скопировано во внутренний буфер");
                    }
                }
            }
            case PASTE -> {
                String clip = null;
                try {
                    clip = GLFW.glfwGetClipboardString(
                        MinecraftClient.getInstance().getWindow().getHandle());
                } catch (RuntimeException | LinkageError ignored) {}
                if (clip == null) clip = localClipboard;
                if (clip != null && !clip.isEmpty()) {
                    field.write(clip);
                    openPage("KEYBOARD");
                } else showStatus("Буфер пуст");
            }
            case SELECT_ALL -> {
                field.setSelectionStart(0);
                field.setSelectionEnd(field.getText().length());
                showStatus("Выделен весь текст");
            }
            case MOVE_STEP -> {
                switch (result.key().value()) {
                    case "LEFT" -> featurePrefs.move(-0.035f, 0, 0);
                    case "RIGHT" -> featurePrefs.move(0.035f, 0, 0);
                    case "UP" -> featurePrefs.move(0, -0.035f, 0);
                    case "DOWN" -> featurePrefs.move(0, 0.035f, 0);
                    case "NEAR" -> featurePrefs.move(0, 0, 0.035f);
                    case "FAR" -> featurePrefs.move(0, 0, -0.035f);
                    default -> { }
                }
                featurePrefs.savePosition();
                showStatus("Положение изменено");
            }
            case MOVE_GRAB -> { /* Held-controller movement is handled by VR plane. */ }
            case MOVE_RESET -> {
                featurePrefs.resetPosition();
                showStatus("Положение сброшено");
            }
            case HAPTIC_TOGGLE -> { featurePrefs.toggleHaptics(); panel = null; }
            case HAPTIC_STRENGTH -> { featurePrefs.adjustHapticStrength(); panel = null; }
            case CONTROLLER_LEFT_TOGGLE -> { featurePrefs.toggleLeft(); panel = null; }
            case CONTROLLER_RIGHT_TOGGLE -> { featurePrefs.toggleRight(); panel = null; }
            case BEAM_SHORTER -> { featurePrefs.adjustBeam(-5); panel = null; }
            case BEAM_LONGER -> { featurePrefs.adjustBeam(5); panel = null; }
            case DOT_SMALLER -> { featurePrefs.adjustDot(-1); panel = null; }
            case DOT_LARGER -> { featurePrefs.adjustDot(1); panel = null; }
            case THEME -> {
                theme = KeyboardTheme.fromName(result.key().value());
                themePrefs.save(theme);
                // Keep the settings page open so another theme can be previewed.
                panel = null;
            }
            case SHIFT -> shift = !shift;
            case COLLAPSE -> { collapsed = true; openPage("KEYBOARD"); }
            case EXPAND -> collapsed = false;
            default -> { }
        }
        if (result.key().command() == KeyboardLayout.Command.TYPE && shift)
            shift = false;
    }


    /**
     * While the custom composer is open, the VR key matrix writes only to the
     * selected draft field. The underlying Minecraft chat is not touched
     * until the dedicated Send button is deliberately pressed.
     */
    private void activateCompose(Screen screen, TextFieldWidget chatField,
                                 KeyHoldController.Activation result) {
        switch (result.key().command()) {
            case COMPOSE_MESSAGE -> quickDraft.selectNote(false);
            case COMPOSE_NOTE -> quickDraft.selectNote(true);
            case COMPOSE_CLEAR -> quickDraft.clearActive();
            case COMPOSE_SEND -> {
                if (editingQuickSlot >= 0) {
                    if (quickBinds.put(editingQuickSlot,
                        quickDraft.message(), quickDraft.note())) {
                        editingQuickSlot = -1;
                        quickDraft.clear();
                        openPage("QUICK");
                        showStatus("Бинд сохранён");
                    } else showStatus("Текст максимум 256, название 60 символов");
                } else sendQuickDraft(screen, chatField);
            }
            case QUICK_MESSAGES, ESCAPE -> {
                editingQuickSlot = -1;
                openPage("QUICK");
            }
            case TYPE -> {
                if (".".equals(result.text())) quickDraft.dot();
                else quickDraft.insert(result.text());
                if (shift) shift = false;
            }
            case SPACE -> quickDraft.insert(" ");
            case BACKSPACE -> quickDraft.backspace();
            case LEFT -> quickDraft.moveCursor(-1);
            case RIGHT -> quickDraft.moveCursor(1);
            case SHIFT -> shift = !shift;
            case LANGUAGE -> { russian = !russian; symbols = false; }
            case SYMBOLS -> symbols = !symbols;
            default -> { }
        }
        panel = null;
    }

    private void sendQuickDraft(Screen screen, TextFieldWidget chatField) {
        if (!(screen instanceof ChatScreen)) {
            showStatus("Отправка доступна в чате Minecraft");
            return;
        }
        if (quickDraft.outgoing().isEmpty()) {
            showStatus("Сначала введи сообщение");
            return;
        }
        if (!quickDraft.canSend()) {
            showStatus("Слишком длинно: максимум 256 символов");
            return;
        }
        if (!chatField.getText().isEmpty()) {
            // Never silently erase an unsent message in the main chat box.
            showStatus("Основная строка чата занята");
            return;
        }
        chatField.setText(quickDraft.outgoing());
        screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        quickDraft.clear();
        openPage("KEYBOARD");
    }

    private void openPage(String page) {
        menuPage = page;
        settingsOpen = !"KEYBOARD".equals(page);
        panel = null;
    }

    private void showStatus(String message) {
        voiceStatus = message;
        voiceStatusAt = System.nanoTime() / 1_000_000L;
    }

    private void startDictation(Screen original, TextFieldWidget destination) {
        // Mic permission and recognition service are owned by Android. The
        // user's spoken words never automatically send a Minecraft message.
        FoxCraftVoiceInput.start(russian, words -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (original == mc.currentScreen &&
                destination == focusedField(original)) {
                destination.write(words);
                showStatus("Речь вставлена в поле");
                openPage("KEYBOARD");
            } else showStatus("Поле изменилось, ввод отменён");
        }, this::showStatus);
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

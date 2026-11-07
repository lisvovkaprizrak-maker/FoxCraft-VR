package dev.foxcraft.keyboard;

/** Pure mode-local menu with the same detached VR panel and controller hit map. */
public final class FreeKeyboardSettingsMenu {
    private static final KeyboardLayout.Command NONE = KeyboardLayout.Command.NONE;
    private static final KeyboardLayout.Command ADJ = KeyboardLayout.Command.ADJUST_OPTION;
    private static final KeyboardLayout.Command PAGE = KeyboardLayout.Command.FREE_OPTIONS;
    private static final KeyboardLayout.Command BACK = KeyboardLayout.Command.SETTINGS_HOME;
    private FreeKeyboardSettingsMenu() {}
    public static KeyboardLayout.Panel make(KeyboardLayout.Panel panel, String page,
            FoxCraftFeatureSettings p, KeyboardTheme theme) {
        if (panel == null) return null;
        return switch (page) {
            case "FREE_SETTINGS" -> KeyboardLayout.toolMenu(panel,
                new String[]{"РАЗМЕР: " + p.freeKeyboardSize() + "%",
                    "РАЗМЕР − 5%", "РАЗМЕР + 5%",
                    "НАКЛОН: " + p.freeKeyboardTilt() + "°", "ПОЗИЦИЯ",
                    "ПРОЗРАЧН.: " + p.freeKeyboardOpacity() + "%",
                    "ТЕМЫ", "КОНТРОЛЛЕРЫ", "СБРОС СВОБ. КЛАВ", "← К КЛАВИАТУРЕ"},
                new KeyboardLayout.Command[]{NONE, ADJ, ADJ, PAGE,
                    PAGE, PAGE, PAGE, PAGE, PAGE, KeyboardLayout.Command.BACK_TO_KEYS},
                new String[]{"", "FREE_SIZE_DOWN", "FREE_SIZE_UP",
                    "FREE_TILT", "FREE_POSITION", "FREE_APPEARANCE",
                    "FREE_THEMES", "FREE_CONTROLLERS", "FREE_RESET", ""});
            case "FREE_TILT" -> KeyboardLayout.toolMenu(panel,
                new String[]{"НАКЛОН: " + p.freeKeyboardTilt() + "°",
                    "НАКЛОН − 5°", "НАКЛОН + 5°", "НАКЛОН: СБРОС", "← НАСТРОЙКИ"},
                new KeyboardLayout.Command[]{NONE, ADJ, ADJ, ADJ, BACK},
                new String[]{"", "FREE_TILT_DOWN", "FREE_TILT_UP", "FREE_TILT_RESET", ""});
            case "FREE_POSITION" -> KeyboardLayout.toolMenu(panel,
                new String[]{"← ЛЕВЕЕ", "ПРАВЕЕ →", "↑ ВЫШЕ", "↓ НИЖЕ",
                    "БЛИЖЕ", "ДАЛЬШЕ",
                    p.freePositionLocked() ? "ЗАМОК: ВКЛ" : "ЗАМОК: ВЫКЛ",
                    "СБРОС ПОЗИЦИИ", "← НАСТРОЙКИ"},
                new KeyboardLayout.Command[]{ADJ, ADJ, ADJ, ADJ,
                    ADJ, ADJ, ADJ, ADJ, BACK},
                new String[]{"FREE_LEFT", "FREE_RIGHT", "FREE_UP", "FREE_DOWN",
                    "FREE_NEAR", "FREE_FAR", "FREE_LOCK", "FREE_POSITION_RESET", ""});
            case "FREE_APPEARANCE" -> KeyboardLayout.toolMenu(panel,
                new String[]{"ПРОЗРАЧН.: " + p.freeKeyboardOpacity() + "%",
                    "ПРОЗРАЧН. −", "ПРОЗРАЧН. +",
                    "ЗАЗОР −", "ЗАЗОР +",
                    p.clickSound() ? "ЗВУК: ВКЛ" : "ЗВУК: ВЫКЛ",
                    "ТЕМЫ", "← НАСТРОЙКИ"},
                new KeyboardLayout.Command[]{NONE, ADJ, ADJ, ADJ, ADJ, ADJ, PAGE, BACK},
                new String[]{"", "FREE_OPACITY_DOWN", "FREE_OPACITY_UP",
                    "FREE_GAP_DOWN", "FREE_GAP_UP", "FREE_SOUND", "FREE_THEMES", ""});
            case "FREE_CONTROLLERS" -> KeyboardLayout.toolMenu(panel,
                new String[]{p.haptics() ? "ВИБРАЦИЯ: ВКЛ" : "ВИБРАЦИЯ: ВЫКЛ",
                    "СИЛА ВИБРАЦИИ: " + p.hapticStrength(),
                    p.leftHand() ? "ЛЕВАЯ: ВКЛ" : "ЛЕВАЯ: ВЫКЛ",
                    p.rightHand() ? "ПРАВАЯ: ВКЛ" : "ПРАВАЯ: ВЫКЛ",
                    "ЛУЧ КОРОЧЕ", "ЛУЧ ДЛИННЕЕ", "ТОЧКА −", "ТОЧКА +",
                    "← НАСТРОЙКИ"},
                new KeyboardLayout.Command[]{ADJ, ADJ, ADJ, ADJ,
                    ADJ, ADJ, ADJ, ADJ, BACK},
                new String[]{"FREE_HAPTICS", "FREE_HAPTIC_STRENGTH",
                    "FREE_LEFT_HAND", "FREE_RIGHT_HAND",
                    "FREE_BEAM_DOWN", "FREE_BEAM_UP",
                    "FREE_DOT_DOWN", "FREE_DOT_UP", ""});
            case "FREE_THEMES" -> KeyboardLayout.themeMenu(panel, theme.name());
            case "FREE_RESET" -> KeyboardLayout.toolMenu(panel,
                new String[]{"СБРОСИТЬ СВОБ. КЛАВ?", "ДА, СБРОС", "НЕТ, НАЗАД"},
                new KeyboardLayout.Command[]{NONE, ADJ, BACK},
                new String[]{"", "FREE_RESET_ALL", ""});
            default -> KeyboardLayout.freeKeyboard(panel);
        };
    }
}

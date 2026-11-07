package dev.foxcraft.keyboard;

/**
 * Visual-only palettes. The five optional themes intentionally avoid fox
 * artwork; CLASSIC remains available as the original FoxCraft identity.
 * No input geometry, controller bindings or screen focus are changed.
 */
public enum KeyboardTheme {
    CLASSIC("FoxCraft", 0xF21C1B23, 0xFF302E3A, 0xFFB45215,
        0xFFFF8B16, 0xFFFF8D20, 0xFFFFFFFF, 0xFFFFBB6D, 0xFFE97510),
    MIDNIGHT("Полночь", 0xF30C1122, 0xFF252A46, 0xFF424B8D,
        0xFF6679E8, 0xFF829AFF, 0xFFF5F7FF, 0xFFA7B7FF, 0xFF5066D1),
    OCEAN("Океан", 0xF2102733, 0xFF173B47, 0xFF236C7A,
        0xFF17B9C8, 0xFF3EE7E2, 0xFFE8FEFF, 0xFF8DEBE8, 0xFF078C9A),
    SAKURA("Сакура", 0xF32B1C31, 0xFF493047, 0xFF985678,
        0xFFF28CB5, 0xFFFFA8D0, 0xFFFFF4FA, 0xFFFFC5E0, 0xFFD85D97),
    AURORA("Сияние", 0xF30C2023, 0xFF203D3B, 0xFF246C59,
        0xFF68E7AF, 0xFF78FFD1, 0xFFE8FFF7, 0xFFB9FCE3, 0xFF20A885),
    NEW_YEAR("Новый год", 0xF3152832, 0xFF24474C, 0xFF347B78,
        0xFF66C6B4, 0xFFFFD16E, 0xFFFFFFFF, 0xFFC4FFF4, 0xFFC53547);

    private final String title;
    private final int panel, key, hover, pressed, accent, text, legend, enter;

    KeyboardTheme(String title, int panel, int key, int hover, int pressed,
                  int accent, int text, int legend, int enter) {
        this.title = title;
        this.panel = panel;
        this.key = key;
        this.hover = hover;
        this.pressed = pressed;
        this.accent = accent;
        this.text = text;
        this.legend = legend;
        this.enter = enter;
    }

    public String title() { return title; }
    public int panel() { return panel; }
    public int key() { return key; }
    public int hover() { return hover; }
    public int pressed() { return pressed; }
    public int accent() { return accent; }
    public int text() { return text; }
    public int legend() { return legend; }
    public int enter() { return enter; }

    public static KeyboardTheme fromName(String name) {
        if (name != null) {
            for (KeyboardTheme theme : values())
                if (theme.name().equalsIgnoreCase(name.trim())) return theme;
        }
        return CLASSIC;
    }
}

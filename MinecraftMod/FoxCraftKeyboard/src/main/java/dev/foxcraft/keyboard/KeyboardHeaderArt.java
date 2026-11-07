package dev.foxcraft.keyboard;

import net.minecraft.client.gui.DrawContext;

/**
 * Animated, non-interactive miniature illustrations for the 5 alternative
 * themes. Drawn inside the top 120x22px crest. CLASSIC is deliberately
 * excluded and retains the original FoxCraft logo exactly as before.
 * No texture pack or additional input widgets are required.
 */
public final class KeyboardHeaderArt {
    private KeyboardHeaderArt() {}

    public static void render(DrawContext c, KeyboardLayout.Panel panel,
                              KeyboardTheme theme, long now) {
        if (theme == KeyboardTheme.CLASSIC) return;
        int left = panel.x() + (panel.width() - 120) / 2;
        int top = panel.y() + 1;
        switch (theme) {
            case MIDNIGHT -> stars(c, left, top, now);
            case OCEAN -> ocean(c, left, top, now);
            case SAKURA -> sakura(c, left, top, now);
            case AURORA -> aurora(c, left, top, now);
            case NEW_YEAR -> christmasTree(c, left, top, now);
            default -> { }
        }
    }

    /** Moon and individually twinkling stars, not a text title. */
    private static void stars(DrawContext c, int x, int y, long now) {
        // Purple-blue night sky, moon on the left.
        disc(c, x + 20, y + 12, 9, 0xFFF8EDC7);
        disc(c, x + 24, y + 9, 8, 0xFF1C203D);
        for (int i = 0; i < 10; i++) {
            int sx = x + 40 + (i * 41) % 76;
            int sy = y + 3 + (i * 13) % 18;
            double wave = (1 + Math.sin(now / 330.0 + i * 1.9)) * 0.5;
            int alpha = 85 + (int) (wave * 170);
            int color = (alpha << 24) | 0x00DAE6FF;
            sparkle(c, sx, sy, 1 + (i % 3 == 0 ? 1 : 0), color);
        }
    }

    /** Three animated wave ribbons and light bubbles in the centre. */
    private static void ocean(DrawContext c, int x, int y, long now) {
        int[] colors = {0xFF2F8FA5, 0xFF54DDE3, 0xFFCCFFFF};
        for (int line = 0; line < 3; line++) {
            int baseline = y + 7 + line * 5;
            for (int i = 4; i < 116; i += 2) {
                int yy = baseline + (int) Math.round(2.0 *
                    Math.sin(i * 0.11 + now / 480.0 + line * 1.5));
                c.fill(x + i, yy, x + i + 2, yy + 1, colors[line]);
            }
        }
        for (int i = 0; i < 5; i++) {
            int bx = x + 13 + i * 23;
            int by = y + 3 + (int) Math.floorMod(i * 7L - now / 220, 18);
            disc(c, bx, by, 2, 0x9979F4FF);
            c.fill(bx, by - 1, bx + 1, by, 0xDFFFFFFF);
        }
    }

    /** Two flowering cherry trees with swaying pink blossoms. */
    private static void sakura(DrawContext c, int x, int y, long now) {
        cherryTree(c, x + 33, y + 23, now, false);
        cherryTree(c, x + 87, y + 23, now, true);
        for (int i = 0; i < 5; i++) {
            int px = x + 14 + (int) Math.floorMod(22L * i + now / 60, 95);
            int py = y + 4 + (int) Math.floorMod(9L * i + now / 125, 18);
            c.fill(px, py, px + 2, py + 2, 0xCFFFC7E6);
        }
    }

    private static void cherryTree(DrawContext c, int x, int bottom,
                                   long now, boolean mirror) {
        int branch = mirror ? -1 : 1;
        stroke(c, x, bottom, x, bottom - 12, 0xFF795064, 2);
        stroke(c, x, bottom - 10, x + branch * 12, bottom - 18,
            0xFF795064, 1);
        stroke(c, x, bottom - 8, x - branch * 11, bottom - 16,
            0xFF795064, 1);
        int sway = (int) Math.round(Math.sin(now / 900.0 + x) * 1.5);
        for (int i = 0; i < 9; i++) {
            int bx = x + sway + ((i * 13) % 29) - 14;
            int by = bottom - 18 + ((i * 7) % 11) - 5;
            disc(c, bx, by, 3, i % 3 == 0 ? 0xFFFFE4F2 : 0xFFFFA1C9);
            c.fill(bx, by, bx + 1, by + 1, 0xFFFFFFFF);
        }
    }

    /** Animated curtains of green, aqua and purple polar light. */
    private static void aurora(DrawContext c, int x, int y, long now) {
        int[] colors = {0x8854F6CE, 0xAA8BE6FF, 0x996FB5F8};
        for (int band = 0; band < colors.length; band++) {
            for (int i = 3; i < 117; i += 2) {
                double phase = i * 0.07 + now / 580.0 + band * 1.35;
                int yy = y + 5 + band * 3 +
                    (int) Math.round(4 * Math.sin(phase));
                c.fill(x + i, yy, x + i + 2, yy + 4, colors[band]);
                c.fill(x + i, yy + 4, x + i + 2, yy + 7,
                    (colors[band] & 0x00FFFFFF) | 0x22000000);
            }
        }
    }

    /** A luminous GREEN Christmas tree and a softly pulsing golden star. */
    private static void christmasTree(DrawContext c, int x, int y, long now) {
        int cx = x + 60;
        // Snowy ground and a little trunk; no "Новый год" lettering.
        c.fill(cx - 32, y + 23, cx + 33, y + 24, 0x99F4FFFF);
        c.fill(cx - 2, y + 20, cx + 3, y + 24, 0xFF795039);
        for (int level = 0; level < 3; level++) {
            int tipY = y + 4 + level * 5;
            int half = 7 + level * 5;
            for (int row = 0; row < 7; row++) {
                int radius = (half * (row + 1)) / 8;
                int yy = tipY + row;
                c.fill(cx - radius, yy, cx + radius + 1, yy + 1,
                    row % 2 == 0 ? 0xFF1B9A61 : 0xFF31CA76);
            }
        }
        sparkle(c, cx, y + 2, 3,
            (((int) (190 + 65 * Math.sin(now / 290.0))) << 24) | 0x00FFE07B);
        int[] ornaments = {-10, 8, -4, 13, -13, 3};
        for (int i = 0; i < ornaments.length; i++) {
            int px = cx + ornaments[i];
            int py = y + 11 + i * 2;
            if (py > y + 22) py = y + 22;
            disc(c, px, py, 1, i % 2 == 0 ? 0xFFF7C963 : 0xFFED5761);
        }
        // A few falling flakes around the tree and inside the crest.
        for (int i = 0; i < 7; i++) {
            int px = x + 6 + i * 17;
            int py = y + (int) Math.floorMod((long) i * 7 + now / 110, 23);
            sparkle(c, px, py, 1, 0xA8ECFAFF);
        }
    }

    private static void sparkle(DrawContext c, int x, int y, int radius, int color) {
        c.fill(x - radius, y, x + radius + 1, y + 1, color);
        c.fill(x, y - radius, x + 1, y + radius + 1, color);
        c.fill(x, y, x + 1, y + 1, 0xFFFFFFFF);
    }

    private static void disc(DrawContext c, int x, int y, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int half = (int) Math.floor(Math.sqrt(radius * radius - dy * dy));
            c.fill(x - half, y + dy, x + half + 1, y + dy + 1, color);
        }
    }

    private static void stroke(DrawContext c, int ax, int ay, int bx, int by,
                               int color, int width) {
        int steps = Math.max(Math.abs(bx - ax), Math.abs(by - ay));
        for (int i = 0; i <= steps; i++) {
            int px = ax + (bx - ax) * i / Math.max(1, steps);
            int py = ay + (by - ay) * i / Math.max(1, steps);
            c.fill(px, py, px + width, py + width, color);
        }
    }
}

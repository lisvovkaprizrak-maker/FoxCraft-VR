import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Build-time-only vector-to-bitmap font rasterizer.
 * Java2D is NOT loaded on Quest; only its generated PNG is packaged.
 * Uses the build JDK's ordinary SansSerif fallback (Cyrillic and Latin).
 */
public final class GenerateHdFont {
    private static final String[] ROWS = {
        "abcdefghijklmnop",
        "qrstuvwxyzABCDEF",
        "GHIJKLMNOPQRSTUV",
        "WXYZабвгдеёжзийк",
        "лмнопрстуфхцчшщъ",
        "ыьэюяАБВГДЕЁЖЗИЙ",
        "КЛМНОПРСТУФХЦЧШЩ",
        "ЪЫЬЭЮЯ0123456789",
        "!@#$%^&*()_-+=[]",
        "{};:'\",.<>/?\\|~\u0060",
        "№×⚙←→⌫ " + "\0".repeat(9)
    };
    private static final int CELL = 32;

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected output directory");
        for (String row : ROWS) {
            if (row.length() != 16)
                throw new IllegalStateException("Font atlas row must contain 16 characters: " + row);
        }
        var canvas = new BufferedImage(CELL * 16, CELL * ROWS.length,
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
            RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setFont(new Font("SansSerif", Font.PLAIN, 25));
        graphics.setColor(Color.WHITE);
        FontMetrics metrics = graphics.getFontMetrics();
        for (int row = 0; row < ROWS.length; row++) {
            for (int col = 0; col < 16; col++) {
                char glyph = ROWS[row].charAt(col);
                if (glyph == '\0' || glyph == ' ') continue;
                String text = Character.toString(glyph);
                int x = col * CELL + (CELL - metrics.stringWidth(text)) / 2;
                int y = row * CELL + (CELL + metrics.getAscent() - metrics.getDescent()) / 2;
                graphics.drawString(text, x, y);
            }
        }
        graphics.dispose();
        Path file = Path.of(args[0]).resolve(
            "assets/foxcraft_keyboard/textures/font/hd_keys.png");
        Files.createDirectories(file.getParent());
        ImageIO.write(canvas, "png", file.toFile());
        System.out.println("[FoxCraft] Generated anti-aliased keyboard font atlas " + file);
    }
}
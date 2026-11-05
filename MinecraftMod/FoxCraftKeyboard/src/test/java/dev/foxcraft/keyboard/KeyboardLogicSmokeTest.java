package dev.foxcraft.keyboard;

import java.util.List;

/** Pure JDK 21 test; no Minecraft, Fabric, or Unity runtime required. */
public final class KeyboardLogicSmokeTest {
    public static void main(String[] args) {
        require(KeyboardLayout.make(219, 200, true, true, false, false, false) == null,
            "Small screens should disable the keyboard");

        for (int[] size : new int[][]{{220, 140}, {320, 180}, {854, 480}, {1200, 700}}) {
            KeyboardLayout.Panel panel = KeyboardLayout.make(
                size[0], size[1], true, true, false, false, false);
            require(panel != null, "Layout available for " + size[0] + "x" + size[1]);
            require(panel.x() >= 0 && panel.y() >= 0, "Panel begins on screen");
            require(panel.x() + panel.width() <= size[0], "Panel fits horizontally");
            require(panel.y() + panel.height() <= size[1], "Panel fits vertically");
            for (KeyboardLayout.Key key : panel.keys()) {
                require(key.x() >= panel.x() && key.y() >= panel.y(), "Key begins inside panel");
                require(key.x() + key.width() <= panel.x() + panel.width(), "Key width fits");
                require(key.y() + key.height() <= panel.y() + panel.height(), "Key height fits");
                require(key.width() > 0 && key.height() > 0, "Positive key size");
            }
        }

        KeyboardLayout.Panel ru = KeyboardLayout.make(854, 480, true, true, false, false, false);
        int digitRowY = key(ru.keys(), "1").y();
        StringBuilder numbers = new StringBuilder();
        for (KeyboardLayout.Key k : ru.keys())
            if (k.y() == digitRowY && k.value().length() == 1)
                numbers.append(k.value());
        require("1234567890".contentEquals(numbers), "Permanent number row: " + numbers);

        KeyboardLayout.Key letter = key(ru.keys(), "й");
        require("~".equals(letter.alternate()), "Russian Й has ~ legend");
        require("@".equals(key(ru.keys(), "ф").alternate()), "Russian Ф has @ legend");
        require("ё".equals(key(ru.keys(), "е").alternate()),
            "Long press on Russian Е must produce Ё");
        require(key(ru.keys(), "ъ").value().equals("ъ"),
            "Russian hard sign must be available as a normal key");
        KeyboardLayout.Panel en = KeyboardLayout.make(854, 480, true, false, false, false, false);
        require("@".equals(key(en.keys(), "q").alternate()), "English Q has @ legend");
        KeyboardLayout.Panel ruShift = KeyboardLayout.make(854, 480, true, true, false, true, false);
        require("Ё".equals(key(ruShift.keys(), "Е").alternate()),
            "Uppercase Е long press must insert uppercase Ё");
        int enDigits = 0;
        int enDigitRow = key(en.keys(), "1").y();
        for (KeyboardLayout.Key key : en.keys())
            if (key.y() == enDigitRow && key.value().matches("[0-9]"))
                enDigits++;
        require(enDigits == 10, "Number row is present in English layout");

        KeyHoldController hold = new KeyHoldController();
        hold.press(letter, 1000);
        require("й".equals(hold.release(letter, 1449).text()), "449 ms: letter");
        hold.press(letter, 2000);
        require("~".equals(hold.release(letter, 2450).text()), "450 ms: alternate symbol");
        hold.press(letter, 3000);
        require(hold.isHolding(letter, 3450), "Hold state activates at 450 ms");
        require(hold.progress(letter, 3450) == 1f, "Hold progress reaches 100 percent");
        require(hold.progress(letter, 3200) > 0.4f &&
            hold.progress(letter, 3200) < 0.5f,
            "Hold progress reports intermediate fraction");
        require(hold.progress(key(ru.keys(), "ц"), 3500) == 0f,
            "Non-pressed keys have no progress");
        require(hold.release(key(ru.keys(), "ц"), 3900) == null, "Drag-away cancels");
        hold.press(letter, 4000);
        hold.cancel();
        require(hold.progress(letter, 5000) == 0f,
            "Focus loss resets progress");
        require(hold.release(letter, 5000) == null, "Focus loss cancels");

        KeyboardLayout.Key yo = key(ru.keys(), "е");
        hold.press(yo, 6000);
        KeyHoldController.Activation yoResult = hold.release(yo, 6500);
        require(yoResult != null && yoResult.alternate() && "ё".equals(yoResult.text()),
            "Long press on Е selects Ё exactly once");

        KeyboardLayout.Panel collapsed = KeyboardLayout.make(854, 480, true, true, false, false, true);
        require(collapsed.keys().stream().anyMatch(k -> k.command() == KeyboardLayout.Command.EXPAND),
            "Collapsed keyboard can reopen");

        System.out.println("FoxCraft keyboard logic smoke tests: OK");
    }

    private static KeyboardLayout.Key key(List<KeyboardLayout.Key> keys, String label) {
        return keys.stream().filter(k -> label.equals(k.label())).findFirst()
            .orElseThrow(() -> new AssertionError("Missing key: " + label));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

package dev.foxcraft.keyboard;

import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

/** Pure JDK 21 test; no Minecraft, Fabric, or Unity runtime required. */
public final class KeyboardLogicSmokeTest {
    public static void main(String[] args) {
        try {
            Path tmp = Files.createTempDirectory("foxcraft-keyboard-pref-test");
            Path settings = tmp.resolve("size.txt");
            KeyboardPreferences pref = new KeyboardPreferences(settings);
            require(pref.loadWidth() == 280, "New VR players default to compact size");
            pref.saveWidth(240);
            require(pref.loadWidth() == 240, "Small size persists across sessions");
            pref.saveWidth(320);
            require(new KeyboardPreferences(settings).loadWidth() == 320,
                "Large size reloads in a fresh keyboard session");
            require(KeyboardPreferences.nextWidth(320) == 240 &&
                KeyboardPreferences.nextWidth(240) == 280 &&
                KeyboardPreferences.nextWidth(280) == 320,
                "Size control cycles through the three available widths");
            Files.writeString(settings, "not-an-option");
            require(pref.loadWidth() == 280, "Invalid saved size safely resets");
            Files.deleteIfExists(settings);
            Files.deleteIfExists(tmp);
        } catch (IOException e) {
            throw new AssertionError("Keyboard preference file test failed", e);
        }

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
        require(ru.width() == 280 && ru.height() <= 165,
            "Compact 280px keyboard includes a 3-candidate correction strip");
        require(ru.keys().stream().anyMatch(k ->
            k.command() == KeyboardLayout.Command.RESIZE && "РАЗМ".equals(k.label())),
            "Headset keyboard contains a size button");
        for (int option : new int[]{240, 280, 320}) {
            KeyboardLayout.Panel sized = KeyboardLayout.make(
                854, 480, true, true, false, false, false, option);
            require(sized.width() == option,
                "Configurable keyboard width matches selected VR size: " + option);
            require(sized.keys().stream().allMatch(k ->
                k.x() >= sized.x() && k.x() + k.width() <= sized.x() + sized.width()),
                "All resized keys remain on panel");
        }
        int digitRowY = key(ru.keys(), "1").y();
        StringBuilder numbers = new StringBuilder();
        for (KeyboardLayout.Key k : ru.keys())
            if (k.y() == digitRowY && k.value().length() == 1)
                numbers.append(k.value());
        require("1234567890".contentEquals(numbers), "Permanent number row: " + numbers);

        require(ru.keys().stream().anyMatch(k ->
            k.command() == KeyboardLayout.Command.ESCAPE && "ВЫХ".equals(k.label())),
            "Russian layout includes an Escape key to close the chat");
        KeyboardLayout.Key letter = key(ru.keys(), "й");
        require("~".equals(letter.alternate()), "Russian Й has ~ legend");
        require("@".equals(key(ru.keys(), "ф").alternate()), "Russian Ф has @ legend");
        require("ё".equals(key(ru.keys(), "е").alternate()),
            "Long press on Russian Е must produce Ё");
        require(key(ru.keys(), "ъ").value().equals("ъ"),
            "Russian hard sign must be available as a normal key");
        KeyboardLayout.Panel en = KeyboardLayout.make(854, 480, true, false, false, false, false);
        require(en.keys().stream().anyMatch(k ->
            k.command() == KeyboardLayout.Command.ESCAPE && "ESC".equals(k.label())),
            "English layout includes Escape for closing Minecraft screens");
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

        KeyboardLayout.Panel symbolPanel = KeyboardLayout.make(854, 480, true,
            false, true, false, false);
        int symbolDigits = 0;
        for (KeyboardLayout.Key key : symbolPanel.keys())
            if (key.command() == KeyboardLayout.Command.TYPE &&
                key.value().matches("[0-9]"))
                symbolDigits++;
        require(symbolDigits == 10, "Symbols mode keeps exactly one 1–0 number row");
        for (String punctuation : new String[]{"@", "#", "/", "?", "\\", "|", "{", "}"})
            require(symbolPanel.keys().stream()
                .anyMatch(k -> punctuation.equals(k.value())),
                "Symbols mode includes: " + punctuation);

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

        KeyboardPointerInput pointer = new KeyboardPointerInput();
        require(!pointer.isPressed(letter), "Idle key is not visually pressed");
        require(pointer.press(ru, letter.x() + 1, letter.y() + 1, 7000),
            "Controller left-button press is captured");
        require(pointer.isPressed(letter),
            "Pressed key highlights immediately before 450 ms hold");
        require(!pointer.isPressed(key(ru.keys(), "ц")),
            "Only the active key highlights");
        KeyboardPointerInput.Release quick = pointer.release(
            ru, letter.x() + 1, letter.y() + 1, 7200);
        require(quick.consumed() && quick.activation() != null &&
            "й".equals(quick.activation().text()), "Pointer tap types a letter exactly once");
        require(!pointer.isPressed(letter), "Key highlight resets after release");
        require(!pointer.release(ru, letter.x() + 1, letter.y() + 1, 7200).consumed(),
            "A second release without a new press is ignored");

        require(pointer.press(ru, letter.x() + 1, letter.y() + 1, 8000),
            "Long press begins");
        require(pointer.progress(letter, 8300) > 0f, "Hold progress is visible");
        KeyboardPointerInput.Release held = pointer.release(
            ru, letter.x() + 1, letter.y() + 1, 8499);
        require(held.consumed() && held.activation() != null &&
            "~".equals(held.activation().text()) && held.activation().alternate(),
            "Controller hold commits symbol without typing the letter");

        // A panel gap has no key, but a matching release must never click
        // through into Minecraft's UI.
        require(pointer.press(ru, ru.x() + 1, ru.y() + 1, 9000),
            "Panel gap press is consumed");
        KeyboardPointerInput.Release gap = pointer.release(
            ru, ru.x() + 1, ru.y() + 1, 9200);
        require(gap.consumed() && gap.activation() == null,
            "Panel gap release is consumed without typing");

        require(pointer.press(ru, letter.x() + 1, letter.y() + 1, 10000),
            "Press starts before pointer drag");
        KeyboardPointerInput.Release dragged = pointer.release(
            ru, -10, -10, 11000);
        require(dragged.consumed() && dragged.activation() == null,
            "Drag-away release is consumed but types nothing");
        require(pointer.press(ru, letter.x() + 1, letter.y() + 1, 12000),
            "Press starts before focus change");
        pointer.cancel();
        require(!pointer.release(ru, letter.x() + 1, letter.y() + 1, 13000).consumed(),
            "Focus change clears pending capture");
        require(!pointer.press(ru, -10, -10, 14000),
            "Outside-keyboard press goes to the underlying screen");

        Object chatInput = new Object();
        Object serverAddressInput = new Object();

        require(pointer.press(ru, letter.x() + 1, letter.y() + 1,
                15_000L, chatInput), "Press binds the chat field");
        KeyboardPointerInput.Release stale = pointer.release(
            ru, letter.x() + 1, letter.y() + 1,
            15_900L, serverAddressInput);
        require(stale.consumed() && stale.activation() == null,
            "A hold cannot type into a different field after focus changes");

        require(pointer.press(ru, letter.x() + 1, letter.y() + 1,
                16_000L, chatInput), "Second press binds the new session");
        KeyboardPointerInput.Release sameField = pointer.release(
            ru, letter.x() + 1, letter.y() + 1,
            16_500L, chatInput);
        require(sameField.consumed() && sameField.activation() != null &&
            "~".equals(sameField.activation().text()),
            "A hold still types when the exact same field remains focused");

        require(pointer.press(ru, ru.x() + 1, ru.y() + 1,
                17_000L, chatInput), "Panel gap captures field token too");
        KeyboardPointerInput.Release gapFocusChanged = pointer.release(
            ru, ru.x() + 1, ru.y() + 1,
            17_900L, serverAddressInput);
        require(gapFocusChanged.consumed() && gapFocusChanged.activation() == null,
            "A changed field cannot cause a gap click-through");

        // Shape-writing: crossing keys is NOT the same as spelling all of them.
        KeyboardLayout.Key yi = key(ru.keys(), "й");
        KeyboardLayout.Key ts = key(ru.keys(), "ц");
        KeyboardLayout.Key u = key(ru.keys(), "у");
        Object swipeField = new Object();
        KeyboardLayout.Key p = key(ru.keys(), "п");
        require(pointer.press(ru, p.x()+p.width()/2., p.y()+p.height()/2.,
            20_000, swipeField), "Swipe begins on initial Russian letter");
        for (String letterName : new String[]{"р", "и", "в", "е", "т"}) {
            KeyboardLayout.Key next = key(ru.keys(), letterName);
            pointer.move(ru, next.x()+next.width()/2.,
                next.y()+next.height()/2., swipeField);
        }
        require("привет".equals(pointer.swipePreview()),
            "Shape writing predicts привет, not every intersected key");
        KeyboardLayout.Key finalT = key(ru.keys(), "т");
        KeyboardPointerInput.Release swipeRelease = pointer.release(ru,
            finalT.x()+finalT.width()/2., finalT.y()+finalT.height()/2.,
            21_000, swipeField);
        require(swipeRelease.consumed() && swipeRelease.activation() != null &&
            "привет".equals(swipeRelease.activation().text()),
            "Controller stroke inserts predicted word only on release");
        require(!swipeRelease.suggestions().isEmpty() &&
            swipeRelease.suggestions().size() <= 3 &&
            swipeRelease.suggestions().get(0).equals("привет"),
            "Release carries ranked correction choices");
        require(pointer.swipePreview().isEmpty(), "Swipe preview resets after release");

        var hello = new java.util.ArrayList<GestureWordRecognizer.Point>();
        for (String ch : new String[]{"h", "e", "l", "l", "o"}) {
            KeyboardLayout.Key letterKey = key(en.keys(), ch);
            hello.add(new GestureWordRecognizer.Point(
                letterKey.x()+letterKey.width()/2.,
                letterKey.y()+letterKey.height()/2.));
        }
        require("hello".equals(GestureWordRecognizer.suggest(en, hello, 3).get(0)),
            "English word paths are recognized with their own layout");

        var word = new java.util.ArrayList<GestureWordRecognizer.Point>();
        for (String ch : new String[]{"с", "л", "о", "в", "о"}) {
            KeyboardLayout.Key letterKey = key(ru.keys(), ch);
            word.add(new GestureWordRecognizer.Point(
                letterKey.x()+letterKey.width()/2.,
                letterKey.y()+letterKey.height()/2.));
        }
        require("слово".equals(GestureWordRecognizer.suggest(ru, word, 3).get(0)),
            "Approximate Russian word shape predicts слово");
        KeyboardLayout.Panel suggestedPanel = KeyboardLayout.make(
            854, 480, true, true, false, false, false, 280,
            java.util.List.of("привет", "приветы", "приветик"));
        require(suggestedPanel.keys().stream().filter(k ->
            k.command() == KeyboardLayout.Command.SUGGESTION).count() == 3,
            "Three suggestions are presented as pressable VR keys");
        KeyboardLayout.Key correctionKey = suggestedPanel.keys().stream()
            .filter(k -> k.command() == KeyboardLayout.Command.SUGGESTION)
            .findFirst().orElseThrow();
        KeyboardPointerInput corrections = new KeyboardPointerInput();
        require(corrections.press(suggestedPanel, correctionKey.x()+1,
            correctionKey.y()+1, 21_200), "Suggestion is clickable");
        KeyboardPointerInput.Release correctionRelease = corrections.release(
            suggestedPanel, correctionKey.x()+1, correctionKey.y()+1, 21_230);
        require(correctionRelease.activation() != null &&
            "привет".equals(correctionRelease.activation().text()),
            "Correction tap returns complete word, not its truncated label");

        require(new VivecraftLeftPointer().poll(854, 480) == null,
            "Optional second-hand Vivecraft bridge does not crash outside VR");

        // Both pointer captures must remain independent, like two VR hands.
        KeyboardPointerInput rightHand = new KeyboardPointerInput();
        KeyboardPointerInput leftHand = new KeyboardPointerInput();
        KeyboardLayout.Key ruT = key(ru.keys(), "т");
        Object sharedChat = new Object();
        require(rightHand.press(ru, yi.x() + 1, yi.y() + 1, 30_000, sharedChat),
            "Right hand captures its letter");
        require(leftHand.press(ru, ruT.x() + 1, ruT.y() + 1, 30_010, sharedChat),
            "Left hand independently captures a different letter");
        require(rightHand.isPressed(yi) && leftHand.isPressed(ruT),
            "Both trigger captures can coexist");
        KeyboardPointerInput.Release leftText = leftHand.release(
            ru, ruT.x() + 1, ruT.y() + 1, 30_080, sharedChat);
        KeyboardPointerInput.Release rightText = rightHand.release(
            ru, yi.x() + 1, yi.y() + 1, 30_120, sharedChat);
        require(leftText.activation() != null && "т".equals(leftText.activation().text()) &&
            rightText.activation() != null && "й".equals(rightText.activation().text()),
            "Left and right triggers commit their own letters without stealing focus");

        // A held single key still returns its alternate character.
        require(pointer.press(ru, yi.x() + 1, yi.y() + 1, 22_000, swipeField),
            "Ordinary long press starts");
        KeyboardPointerInput.Release ordinaryHold = pointer.release(
            ru, yi.x() + 1, yi.y() + 1, 22_500, swipeField);
        require(ordinaryHold.activation() != null && "~".equals(ordinaryHold.activation().text()),
            "Long-press symbols remain available without swipe");

        // Moving to a different text field cancels an in-progress word.
        require(pointer.press(ru, yi.x() + 1, yi.y() + 1, 23_000, swipeField),
            "Swipe starts before focus change");
        pointer.move(ru, u.x() + 1, u.y() + 1, serverAddressInput);
        KeyboardPointerInput.Release cancelledSwipe = pointer.release(
            ru, u.x() + 1, u.y() + 1, 23_500, serverAddressInput);
        require(cancelledSwipe.consumed() && cancelledSwipe.activation() == null,
            "Changing focused field during swipe cannot type into the wrong UI");

        // Dragging out of the keyboard must never commit a partial gesture.
        require(pointer.press(ru, yi.x() + 1, yi.y() + 1, 24_000, swipeField),
            "Start swipe before dragging away");
        pointer.move(ru, ts.x() + 1, ts.y() + 1, swipeField);
        KeyboardPointerInput.Release outsideSwipe = pointer.release(
            ru, -100, -100, 24_700, swipeField);
        require(outsideSwipe.consumed() && outsideSwipe.activation() == null,
            "Swipe released outside keyboard is cancelled");

        // Ignore digits and command keys when collecting a swipe.
        SwipeGesture pureSwipe = new SwipeGesture();
        pureSwipe.begin(key(ru.keys(), "1"), 0, 0);
        pureSwipe.move(ru, yi.x() + 1, yi.y() + 1);
        require(pureSwipe.preview().isEmpty(), "Number keys cannot begin swipe typing");

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

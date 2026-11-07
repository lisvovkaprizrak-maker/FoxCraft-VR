package dev.foxcraft.keyboard;

import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

/** Pure JDK 21 test; no Minecraft, Fabric, or Unity runtime required. */
public final class KeyboardLogicSmokeTest {
    public static void main(String[] args) {
        testQuickMessageComposer();
        testQuickBinds();
        // Regression: on Quest, the world-selection text field must not
        // display FoxCraft over the menu, while in-world chat stays usable
        // even if the optional 3D Vivecraft hook never starts.
        require(!KeyboardDisplayPolicy.showScreenOverlay(true, false, false),
            "VR world-selection menus must not have a screen-embedded keyboard");
        require(KeyboardDisplayPolicy.showScreenOverlay(true, true, false),
            "VR chat must retain keyboard fallback if room panel is missing");
        require(!KeyboardDisplayPolicy.showScreenOverlay(true, true, true),
            "VR chat must not duplicate a working detached keyboard");
        require(!KeyboardDisplayPolicy.showScreenOverlay(false, false, false),
            "World-selection menus must not be covered even when VR_RUNNING is false");
        require(KeyboardDisplayPolicy.showScreenOverlay(false, true, false),
            "Desktop chat retains the ordinary 2D keyboard");
        require(!KeyboardDisplayPolicy.showScreenOverlay(true, false, false, true),
            "Auto-focused world search must not cover world-selection buttons");
        // The separate world-space VR panel MUST use the same request policy,
        // or auto-focused world lists get a second overlapping keyboard.
        require(!KeyboardDisplayPolicy.showRequested(false, true, false),
            "VR plane must stay hidden on auto-focused world search");
        require(KeyboardDisplayPolicy.showRequested(false, true, true),
            "VR plane opens after an explicit edit-field click");
        require(KeyboardDisplayPolicy.showRequested(true, true, false),
            "Chat continues opening immediately with focused input");
        require(!KeyboardDisplayPolicy.showRequested(true, false, true),
            "No VR keyboard is drawn after the focused field disappears");
        require(!KeyboardDisplayPolicy.showScreenOverlay(false, false, false, true),
            "Auto-focused menu input must not open the keyboard outside VR");
        require(KeyboardDisplayPolicy.showScreenOverlay(true, false, false, true, true),
            "Clicking a VR menu text field opens fallback when no panel exists");
        require(KeyboardDisplayPolicy.showScreenOverlay(false, false, false, true, true),
            "Clicked field opens fallback even if VR_RUNNING temporarily reports false");
        require(!KeyboardDisplayPolicy.showScreenOverlay(true, false, true, true, true),
            "Do not duplicate an available detached VR panel");
        require(!KeyboardDisplayPolicy.showScreenOverlay(true, false, false, false, true),
            "Menu keyboard closes when editable focus is lost");
        require(!KeyboardDisplayPolicy.showScreenOverlay(true, false, false, false),
            "No keyboard over menu without active editable focus");
        require(!KeyboardDisplayPolicy.showScreenOverlay(true, false, true, true),
            "Working detached keyboard must not be duplicated on menu");
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

        KeyboardLayout.Panel menuSource = KeyboardLayout.make(
            854, 480, false, true, false, false, false);
        KeyboardLayout.Panel menuLeftField = KeyboardLayout.dockForMenu(
            menuSource, 854, 480, 35, 30, 200, 20);
        require(menuLeftField.x() > 854 / 2,
            "Menu field on left docks keyboard to the right half");
        require(menuLeftField.y() >= 8 &&
            menuLeftField.y() + menuLeftField.height() <= 472,
            "Docked keyboard remains on the menu screen");
        require(menuLeftField.keys().stream().allMatch(k ->
            k.x() >= menuLeftField.x() &&
            k.x() + k.width() <= menuLeftField.x() + menuLeftField.width()),
            "Menu dock moves all keys together");
        KeyboardLayout.Panel menuRightField = KeyboardLayout.dockForMenu(
            menuSource, 854, 480, 640, 340, 180, 20);
        require(menuRightField.x() == 8,
            "Menu field on right docks keyboard to left edge");
        KeyboardLayout.Panel menuWideField = KeyboardLayout.dockForMenu(
            menuSource, 854, 480, 0, 350, 854, 24);
        require(menuWideField.y() + menuWideField.height() <= 342,
            "Wide bottom text field never sits under the docked keyboard");

        KeyboardLayout.Panel ru = KeyboardLayout.make(854, 480, true, true, false, false, false);
        require(ru.width() == 390 && ru.height() <= 190,
            "Reference-width keyboard includes branding and stays compact");
        KeyboardLayout.Panel chatAnchored = KeyboardLayout.make(
            854, 480, true, true, false, false, false, 280, List.of(), 250);
        require(chatAnchored.y() >= 258,
            "Chat keyboard should float below the active chat input field");
        require(chatAnchored.y() >= 250 + 8,
            "Chat keyboard must not overlap the chat text box");
        KeyboardLayout.Panel chatFallbackAbove = KeyboardLayout.make(
            854, 480, true, true, false, false, false, 280, List.of(), 455);
        require(chatFallbackAbove.y() + chatFallbackAbove.height() <= 447,
            "If there is no room below, keyboard stays above the input field");
        KeyboardLayout.Panel fallbackChat = KeyboardLayout.make(
            854, 480, true, true, false, false, false);
        require(fallbackChat.y() > 8,
            "Chat layout fallback also uses a lower floating position");
        require(ru.keys().stream().noneMatch(k ->
            k.command() == KeyboardLayout.Command.RESIZE),
            "Size control must be removed for the fixed reference design");
        for (int oldPreference : new int[]{240, 280, 320}) {
            KeyboardLayout.Panel sized = KeyboardLayout.make(
                854, 480, true, true, false, false, false, oldPreference);
            require(sized.width() == 390,
                "Old saved width preferences cannot resize the reference keyboard");
            require(sized.keys().stream().allMatch(k ->
                k.x() >= sized.x() && k.x() + k.width() <= sized.x() + sized.width()),
                "All fixed-size keys remain on panel");
        }
        KeyboardLayout.Panel detached = KeyboardLayout.make(
            854, 480, false, true, false, false, false, 280,
            List.of("привет", "слово"), -1, true);
        require(detached != null && detached.y() == (480 - detached.height()) / 2,
            "Standalone VR panel centres visible FoxCraft keys at its world anchor");
        require(detached.keys().stream().allMatch(k ->
            k.y() >= detached.y() && k.y() + k.height() <= detached.y() + detached.height()),
            "All detached VR keys remain inside the framebuffer's visible panel");
        KeyboardLayout.Panel lowerChat = KeyboardLayout.make(
            854, 480, true, true, false, false, false, 280,
            List.of(), 180, false);
        require(lowerChat.y() > 180,
            "Regular 2D chat fallback stays below its text field when space exists");
        int digitRowY = key(ru.keys(), "1").y();
        StringBuilder numbers = new StringBuilder();
        for (KeyboardLayout.Key k : ru.keys())
            if (k.y() == digitRowY && k.value().length() == 1)
                numbers.append(k.value());
        require("1234567890".contentEquals(numbers), "Permanent number row: " + numbers);

        require(ru.keys().stream().anyMatch(k ->
            k.command() == KeyboardLayout.Command.TYPE && ",".equals(k.value())),
            "Russian main keyboard replaces redundant exit with comma");
        // Comma and period must be neighbouring keys on the main row in both languages.
        for (KeyboardLayout.Panel punctuationPanel : new KeyboardLayout.Panel[]{
                ru, KeyboardLayout.make(854, 480, true, false, false, false, false)}) {
            KeyboardLayout.Key comma = key(punctuationPanel.keys(), ",");
            KeyboardLayout.Key period = key(punctuationPanel.keys(), ".");
            require(comma.y() == period.y() && comma.x() + comma.width() + 2 == period.x(),
                "Comma is immediately next to period on both language layouts");
        }
        KeyboardLayout.Key letter = key(ru.keys(), "й");
        require("~".equals(letter.alternate()), "Russian Й has ~ legend");
        require("@".equals(key(ru.keys(), "ф").alternate()), "Russian Ф has @ legend");
        require("ё".equals(key(ru.keys(), "е").alternate()),
            "Long press on Russian Е must produce Ё");
        require(key(ru.keys(), "ъ").value().equals("ъ"),
            "Russian hard sign must be available as a normal key");
        KeyboardLayout.Panel en = KeyboardLayout.make(854, 480, true, false, false, false, false);
        require(en.keys().stream().anyMatch(k ->
            k.command() == KeyboardLayout.Command.TYPE && ".".equals(k.value())),
            "English main keyboard replaces redundant collapse with period");
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

        // Backspace tap deletes once; long press repeats one character at a time.
        KeyboardLayout.Key backspace = ru.keys().stream()
            .filter(k -> k.command() == KeyboardLayout.Command.BACKSPACE)
            .findFirst().orElseThrow();
        KeyboardPointerInput deleting = new KeyboardPointerInput();
        Object deleteField = new Object();
        int bx = backspace.x() + backspace.width() / 2;
        int by = backspace.y() + backspace.height() / 2;
        require(deleting.press(ru, bx, by, 40000, deleteField), "Backspace captures");
        require(!deleting.repeatBackspace(40449, deleteField),
            "No repeated deletion before 450 ms");
        KeyboardPointerInput.Release shortDelete = deleting.release(
            ru, bx, by, 40449, deleteField);
        require(shortDelete.activation() != null &&
            shortDelete.activation().key().command() == KeyboardLayout.Command.BACKSPACE,
            "Short Backspace tap deletes one character on release");
        require(deleting.press(ru, bx, by, 41000, deleteField),
            "Backspace hold starts");
        require(!deleting.repeatBackspace(41449, deleteField), "Hold delay respected");
        require(deleting.repeatBackspace(41450, deleteField),
            "Hold deletes first character at 450 ms");
        require(!deleting.repeatBackspace(41534, deleteField),
            "Repeat never fires earlier than 85 ms");
        require(deleting.repeatBackspace(41535, deleteField),
            "Hold deletes next character gradually");
        KeyboardPointerInput.Release longDelete = deleting.release(
            ru, bx, by, 41600, deleteField);
        require(longDelete.consumed() && longDelete.activation() == null,
            "Releasing held Backspace must not add another deletion");
        require(!deleting.repeatBackspace(42000, deleteField),
            "Releasing stops Backspace repeat");
        require(deleting.press(ru, bx, by, 43000, deleteField),
            "Backspace begins before focus changes");
        require(!deleting.repeatBackspace(44000, new Object()),
            "Backspace cannot delete into another field");
        deleting.move(ru, -100, -100, deleteField);
        require(!deleting.repeatBackspace(45000, deleteField),
            "Moving off Backspace stops repeat");
        deleting.release(ru, -100, -100, 45000, deleteField);
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
        // Hand tracking does not stop precisely at the centre of each key.
        var imprecise = new java.util.ArrayList<GestureWordRecognizer.Point>();
        int jitter = 0;
        for (String ch : new String[]{"п", "р", "и", "в", "е", "т"}) {
            KeyboardLayout.Key letterKey = key(ru.keys(), ch);
            double offset = (jitter++ % 2 == 0) ? 3.0 : -3.0;
            imprecise.add(new GestureWordRecognizer.Point(
                letterKey.x()+letterKey.width()/2. + offset,
                letterKey.y()+letterKey.height()/2. - offset));
        }
        require("привет".equals(GestureWordRecognizer.suggest(ru, imprecise, 3).get(0)),
            "Imprecise controller path should still rank привет first");

        KeyboardLayout.Key hardSign = key(ru.keys(), "ъ");
        KeyboardPointerInput noWord = new KeyboardPointerInput();
        require(noWord.press(ru, hardSign.x()+2, hardSign.y()+2, 21_050, swipeField),
            "Unknown word gesture starts normally");
        noWord.move(ru, yi.x()+2, yi.y()+2, swipeField);
        KeyboardPointerInput.Release unknownRelease = noWord.release(
            ru, yi.x()+2, yi.y()+2, 21_180, swipeField);
        require(unknownRelease.consumed() &&
            (unknownRelease.activation() == null ||
             (!unknownRelease.suggestions().isEmpty() &&
              unknownRelease.suggestions().get(0).equals(
                  unknownRelease.activation().text()))),
            "Uncertain gestures can only insert dictionary suggestions, not raw crossings");
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

        // Settings are a mode within the existing VR keyboard panel: no
        // extra text field, no change to coordinates or fixed dimensions.
        require(ru.keys().stream().anyMatch(k ->
            k.command() == KeyboardLayout.Command.SETTINGS),
            "Gear key must open theme settings");
        KeyboardLayout.Panel settings = KeyboardLayout.themeMenu(ru, "NEW_YEAR");
        require(settings.width() == ru.width() && settings.height() == ru.height(),
            "Theme picker keeps the fixed VR keyboard dimensions");
        require(settings.keys().stream().filter(k ->
            k.command() == KeyboardLayout.Command.THEME).count() == 6,
            "Theme picker offers classic plus five custom themes");
        require(settings.keys().stream().anyMatch(k ->
            "NEW_YEAR".equals(k.value()) && k.label().contains("Новый год")),
            "New Year theme is selectable");
        require(settings.keys().stream().anyMatch(k ->
            k.command() == KeyboardLayout.Command.BACK_TO_KEYS),
            "Theme picker has a return control");
        require(settings.keys().stream().noneMatch(k ->
            k.command() == KeyboardLayout.Command.TYPE ||
            k.command() == KeyboardLayout.Command.RESIZE),
            "Theme picker cannot type or resize keyboard");
        require(settings.keys().stream().allMatch(k ->
            k.x() >= settings.x() && k.x() + k.width() <=
                settings.x() + settings.width() &&
            k.y() >= settings.y() && k.y() + k.height() <=
                settings.y() + settings.height()),
            "Every theme choice fits the VR panel");

        // Navigation and command typing remain on the main layout.
        require(ru.keys().stream().anyMatch(k ->
            k.command() == KeyboardLayout.Command.TYPE && "/".equals(k.value())),
            "Standalone slash is available without switching symbol pages");
        require(ru.keys().stream().anyMatch(k ->
            k.command() == KeyboardLayout.Command.HISTORY_PREVIOUS),
            "Previous chat message has a dedicated key");
        require(ru.keys().stream().anyMatch(k ->
            k.command() == KeyboardLayout.Command.HISTORY_NEXT),
            "Next chat message has a dedicated key");
        KeyboardLayout.Panel enMain = KeyboardLayout.make(
            854, 480, true, false, false, false, false);
        require(enMain.keys().stream().anyMatch(k ->
            k.command() == KeyboardLayout.Command.TYPE && "/".equals(k.value())),
            "Slash must remain on the English layout");
        for (KeyboardLayout.Panel current : new KeyboardLayout.Panel[]{ru, enMain}) {
            require(current.keys().stream().allMatch(k ->
                k.x() >= current.x() && k.x() + k.width() <=
                    current.x() + current.width() &&
                k.y() >= current.y() && k.y() + k.height() <=
                    current.y() + current.height()),
                "Added control keys never exceed the fixed keyboard bounds");
        }

        // Russian swipe always starts uppercase. Auto punctuation spacing
        // must avoid doubled spaces and preserve the word correction suffix.
        require("Привет".equals(KeyboardLayout.swipeWord("привет", true, false, true)),
            "Swipe Russian first word at line start is capitalized");
        require("Спасибо".equals(KeyboardLayout.swipeWord("спасибо", true, true, false)),
            "Russian capitalisation is stable with Shift");
        require("hello".equals(KeyboardLayout.swipeWord("hello", false, false, true)),
            "English words retain lowercase unless Shift requested");
        require("Hello".equals(KeyboardLayout.swipeWord("hello", false, true, false)),
            "English Shift still capitalises swipe");
        require("привет".equals(KeyboardLayout.swipeWord("привет", true, false, false)),
            "Middle-of-line Russian swipe stays lowercase without Shift");
        require("".equals(KeyboardLayout.leadingSwipeSpace("", 0)),
            "First swipe in an empty field must not start with a space");
        require(" ".equals(KeyboardLayout.leadingSwipeSpace("Привет", 6)),
            "A second swipe follows the first with exactly one separator");
        require(" ".equals(KeyboardLayout.leadingSwipeSpace("Привет", 6)),
            "A swipe after manually typed text also requires one separator");
        require("".equals(KeyboardLayout.leadingSwipeSpace("Привет ", 7)),
            "A manual space before a swipe must never be doubled");
        require(" ".equals(KeyboardLayout.leadingSwipeSpace("Привет,", 7)),
            "A swipe following punctuation needs a separator");
        require("".equals(KeyboardLayout.leadingSwipeSpace("Привет", 0)),
            "Inserting at the beginning of existing text cannot prepend whitespace");
        require(" ".equals(KeyboardLayout.trailingWordSpace("Привет", 6)),
            "Explicit punctuation may insert one trailing space");
        require("".equals(KeyboardLayout.trailingWordSpace("Привет уже", 6)),
            "Existing following space must not be duplicated");
        require(KeyboardLayout.trimSpaceBeforeDot("Привет ", 7),
            "Dot removes trailing auto-space before punctuation");
        require(!KeyboardLayout.trimSpaceBeforeDot("Привет", 6),
            "Dot after word must not erase a letter");
        require(" ".equals(KeyboardLayout.trailingWordSpace("Привет.", 7)),
            "Dot is followed by a space");

        // New top-level actions must be reachable without shrinking the
        // fixed physical VR panel or removing existing language keys.
        require(ru.keys().stream().anyMatch(k ->
            k.command() == KeyboardLayout.Command.MICROPHONE),
            "Voice microphone action exists on the main keyboard");
        require(ru.keys().stream().anyMatch(k ->
            k.command() == KeyboardLayout.Command.QUICK_MESSAGES),
            "Quick phrases action exists on the main keyboard");
        require(ru.keys().stream().anyMatch(k ->
            k.command() == KeyboardLayout.Command.CLIPBOARD),
            "Clipboard has a dedicated main keyboard button");
        require(ru.keys().stream().anyMatch(k ->
            k.command() == KeyboardLayout.Command.BACKSPACE &&
                k.y() < key(ru.keys(), "1").y()),
            "Delete moved to the top-right controls");
        require(ru.keys().stream().anyMatch(k ->
            k.command() == KeyboardLayout.Command.TYPE && "!".equals(k.value()) &&
                k.y() < key(ru.keys(), "1").y()),
            "Exclamation mark is directly available in the top toolbar");
        require(ru.keys().stream().noneMatch(k ->
            k.command() == KeyboardLayout.Command.COLLAPSE ||
                k.command() == KeyboardLayout.Command.ESCAPE),
            "No redundant bottom-left exit or input-adjacent collapse button");
        KeyboardLayout.Panel toolbox = KeyboardLayout.toolMenu(ru,
            new String[]{"Темы", "Микрофон", "Фразы", "Буфер", "Переместить",
                "Контроллеры", "Назад"},
            new KeyboardLayout.Command[]{KeyboardLayout.Command.THEMES_PAGE,
                KeyboardLayout.Command.MICROPHONE,
                KeyboardLayout.Command.QUICK_MESSAGES,
                KeyboardLayout.Command.CLIPBOARD,
                KeyboardLayout.Command.MOVE_MENU,
                KeyboardLayout.Command.CONTROLLER_MENU,
                KeyboardLayout.Command.BACK_TO_KEYS},
            new String[]{"", "", "", "", "", "", ""});
        require(toolbox.width() == ru.width() && toolbox.height() == ru.height(),
            "Feature settings preserve original VR panel footprint");
        require(toolbox.keys().stream().allMatch(k ->
            k.x() >= toolbox.x() && k.x() + k.width() <=
                toolbox.x() + toolbox.width() &&
            k.y() >= toolbox.y() && k.y() + k.height() <=
                toolbox.y() + toolbox.height()),
            "Controller tool keys stay inside visible keyboard panel");


        System.out.println("FoxCraft keyboard logic smoke tests: OK");
    }

    private static void testQuickBinds() {
        try {
            Path config = Files.createTempFile("foxcraft-binds-", ".properties");
            Files.deleteIfExists(config);
            QuickBindingStore store = new QuickBindingStore(config);
            require(store.count() == 6 && store.pages() == 1,
                "New players start with six binds, not thirty unneeded placeholders");
            require("Привет!".equals(store.message(0)),
                "Original first starter bind remains");
            require("Привет!".equals(store.outgoing(0)),
                "Only the left field goes into chat");
            require(store.put(0, "/home", "Домой"),
                "A bind accepts separate command and button name");
            require("/home".equals(store.outgoing(0)),
                "Right label is never appended to the slash command");
            require("Домой".equals(store.pageLabels(0)[0]),
                "Visible bind button displays its right-hand name");

            // Dynamic addition must create pages only as needed, not limit
            // users to the original static 30 slots.
            for (int i = 0; i < 31; i++)
                require(store.add() == i + 6, "New bind index is append-only");
            require(store.count() == 37 && store.pages() == 7,
                "More than 30 binds can be added on demand");
            require(store.put(36, "/spawn", "Спавн"),
                "User can edit a bind well beyond former 30-slot limit");
            require("Спавн".equals(store.pageLabels(6)[0]),
                "Last dynamic page displays title rather than command");

            require(store.remove(0), "Any existing bind can be deleted");
            require(store.count() == 36 && store.pages() == 6,
                "Deletion compacts slots and removes unneeded trailing pages");
            require("Где ты?".equals(store.outgoing(0)),
                "Remaining bind commands shift cleanly after deletion");
            require("Спавн".equals(store.pageLabels(5)[5]),
                "Last bind retains its title after compaction");

            QuickBindingStore reloaded = new QuickBindingStore(config);
            require(reloaded.count() == 36 &&
                "Спавн".equals(reloaded.pageLabels(5)[5]) &&
                "/spawn".equals(reloaded.outgoing(35)),
                "All inserted/deleted binds persist across restarts");
            KeyboardLayout.Panel original = KeyboardLayout.make(
                854, 480, true, true, false, false, false);
            for (int page = 0; page < reloaded.pages(); page++) {
                KeyboardLayout.Panel binds = KeyboardLayout.quickBindingsMenu(
                    original, reloaded.pageLabels(page), page, reloaded.pages());
                require(binds.width() == original.width() &&
                    binds.height() == original.height(),
                    "Dynamic pages preserve calibrated VR panel size");
                require(binds.keys().stream().filter(k ->
                    k.command() == KeyboardLayout.Command.QUICK_MESSAGE).count() == 6 &&
                    binds.keys().stream().filter(k ->
                    k.command() == KeyboardLayout.Command.QUICK_EDIT).count() == 6 &&
                    binds.keys().stream().filter(k ->
                    k.command() == KeyboardLayout.Command.QUICK_DELETE).count() == 6,
                    "Every card has independent name edit and remove controls");
                require(binds.keys().stream().anyMatch(k ->
                    k.command() == KeyboardLayout.Command.QUICK_ADD),
                    "Every page has an add bind button");
                for (KeyboardLayout.Key key : binds.keys()) {
                    if (key.command() != KeyboardLayout.Command.QUICK_EDIT &&
                        key.command() != KeyboardLayout.Command.QUICK_DELETE) continue;
                    require(binds.at(key.x() + key.width() / 2.0,
                        key.y() + key.height() / 2.0) == key,
                        "Overlay edit/delete buttons must win over full card");
                }
            }

            // Migration from old 30-slot config preserves nonempty entries
            // instead of losing users' commands and annotations.
            Path legacy = Files.createTempFile("foxcraft-binds-legacy-", ".properties");
            java.util.Properties old = new java.util.Properties();
            old.setProperty("text.0", "/home");
            old.setProperty("note.0", "Домой");
            old.setProperty("text.29", "/warp");
            old.setProperty("note.29", "Телепорт");
            try (java.io.OutputStream out = Files.newOutputStream(legacy)) {
                old.store(out, "old quick-bind file");
            }
            QuickBindingStore migrated = new QuickBindingStore(legacy);
            require(migrated.count() == 30 &&
                "/home".equals(migrated.outgoing(0)) &&
                "Домой".equals(migrated.pageLabels(0)[0]) &&
                "/warp".equals(migrated.outgoing(29)),
                "Legacy fixed binds are preserved and labels are converted");

            Files.deleteIfExists(config);
            Files.deleteIfExists(legacy);
        } catch (IOException e) {
            throw new AssertionError("Bind settings test failed", e);
        }
    }

    private static KeyboardLayout.Key key(List<KeyboardLayout.Key> keys, String label) {
        return keys.stream().filter(k -> label.equals(k.label())).findFirst()
            .orElseThrow(() -> new AssertionError("Missing key: " + label));
    }

    private static void testQuickMessageComposer() {
        QuickMessageDraft draft = new QuickMessageDraft();
        require(!draft.canSend(), "Empty custom message must not be sent");
        draft.insert("Привет");
        draft.insert(" мир");
        require("Привет мир".equals(draft.message()),
            "Typing words must not insert automatic spaces");
        draft.clear();
        draft.insertSwipeWord("Привет");
        require("Привет".equals(draft.message()),
            "First compose swipe must not insert leading whitespace");
        draft.insertSwipeWord("мир");
        require("Привет мир".equals(draft.message()),
            "Compose swipes are separated by one space");
        draft.insert("!");
        draft.insertSwipeWord("всем");
        require("Привет мир! всем".equals(draft.message()),
            "Manual typing followed by swipe must also be separated");
        draft.insert(" ");
        draft.insertSwipeWord("друзья");
        require("Привет мир! всем друзья".equals(draft.message()),
            "Existing space must not be duplicated in composer");
        draft.clear();
        draft.insert("Привет мир");
        draft.dot();
        require("Привет мир. ".equals(draft.message()),
            "Dot must add one space after the sentence");
        draft.selectNote(true);
        draft.insert("важное примечание");
        require(draft.editingNote() && "важное примечание".equals(draft.note()),
            "Note is an independent editable field");
        require("Привет мир.".equals(draft.outgoing()),
            "Button title stays out of the sent Minecraft message");
        draft.backspace();
        require(draft.note().endsWith("примечани"),
            "Backspace edits the selected note without altering message");
        draft.selectNote(false);
        draft.moveCursor(-2);
        draft.insert("!");
        require(draft.note().endsWith("примечани"),
            "Switching back must not alter the note");
        require(draft.canSend(), "Valid message with note is sendable");
        draft.clear();
        require(draft.message().isEmpty() && draft.note().isEmpty() && !draft.canSend(),
            "Composer must reset cleanly");

        KeyboardLayout.Panel original = KeyboardLayout.make(
            854, 480, true, true, false, false, false);
        KeyboardLayout.Panel editor = KeyboardLayout.composeMenu(
            original, "Привет", "заметка", false, true, false);
        require(editor.width() == original.width() && editor.height() == original.height(),
            "Editor must preserve VR panel size and controller alignment");
        require(editor.keys().stream().anyMatch(k ->
            k.command() == KeyboardLayout.Command.COMPOSE_MESSAGE) &&
            editor.keys().stream().anyMatch(k ->
            k.command() == KeyboardLayout.Command.COMPOSE_NOTE) &&
            editor.keys().stream().anyMatch(k ->
            k.command() == KeyboardLayout.Command.COMPOSE_SEND),
            "Editor provides message, note, and explicit Send controls");
        require(editor.keys().stream().anyMatch(k ->
            k.command() == KeyboardLayout.Command.TYPE &&
            "й".equals(k.value())),
            "Editor retains the physical Russian keyboard");
        for (KeyboardLayout.Key key : editor.keys())
            require(editor.at(key.x() + key.width() / 2.0,
                key.y() + key.height() / 2.0) != null,
                "Composer keys must remain hit-testable by VR controllers");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

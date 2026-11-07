package dev.foxcraft.keyboard;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Controller/accessibility and room-space placement preferences. No chat
 * history, dictation transcripts or clipboard contents are persisted.
 */
public final class FoxCraftFeatureSettings {
    private final Path path;
    private boolean haptics = true;
    private int hapticStrength = 200;
    private boolean leftHand = true;
    private boolean rightHand = true;
    private int beamCentimetres = 38;
    private int dotRadius = 2;
    private float offsetRight;
    private float offsetDown;
    private float offsetForward;
    // Independent accessibility options; no typed content is recorded here.
    private float historyHeight;
    private int inputY;
    private int keyboardSize = 100;
    private int keyboardTilt = -30;
    private int opacity = 100;
    private int chatTextSize = 100;
    private int swipeSensitivity = 100;
    private int keyGap = 2;
    private int longPressMs = 450;
    private boolean clickSound;
    private boolean wordSuggestions = true;
    private boolean autoCorrect = true;
    private boolean autoSpace = true;
    private boolean positionLocked;

    public FoxCraftFeatureSettings(Path path) {
        this.path = path;
        load();
    }

    public boolean haptics() { return haptics; }
    public int hapticStrength() { return haptics ? hapticStrength : 0; }
    public boolean leftHand() { return leftHand; }
    public boolean rightHand() { return rightHand; }
    public int beamCentimetres() { return beamCentimetres; }
    public int dotRadius() { return dotRadius; }
    public float offsetRight() { return offsetRight; }
    public float offsetDown() { return offsetDown; }
    public float offsetForward() { return offsetForward; }
    public float historyHeight() { return historyHeight; }
    public int inputY() { return inputY; }
    public int keyboardSize() { return keyboardSize; }
    public int keyboardTilt() { return keyboardTilt; }
    public int opacity() { return opacity; }
    public int chatTextSize() { return chatTextSize; }
    public int swipeSensitivity() { return swipeSensitivity; }
    public int keyGap() { return keyGap; }
    public int longPressMs() { return longPressMs; }
    public boolean clickSound() { return clickSound; }
    public boolean wordSuggestions() { return wordSuggestions; }
    public boolean autoCorrect() { return autoCorrect; }
    public boolean autoSpace() { return autoSpace; }
    public boolean positionLocked() { return positionLocked; }
    public void togglePositionLock() { positionLocked = !positionLocked; save(); }
    public void toggleClickSound() { clickSound = !clickSound; save(); }
    public void toggleWordSuggestions() { wordSuggestions = !wordSuggestions; save(); }
    public void toggleAutoCorrect() { autoCorrect = !autoCorrect; save(); }
    public void toggleAutoSpace() { autoSpace = !autoSpace; save(); }

    public void adjustHistoryHeight(int centimetres) {
        historyHeight = clamp(historyHeight + centimetres / 100f, -0.35f, 0.35f);
        save();
    }
    public void adjustInputY(int pixels) {
        inputY = clamp(inputY + pixels, -90, 90);
        save();
    }
    public void adjustKeyboardSize(int percent) {
        keyboardSize = clamp(keyboardSize + percent, 70, 140);
        save();
    }
    public void adjustKeyboardTilt(int degrees) {
        keyboardTilt = clamp(keyboardTilt + degrees, -65, 20);
        save();
    }
    public void adjustOpacity(int percent) {
        opacity = clamp(opacity + percent, 30, 100);
        save();
    }
    public void adjustChatTextSize(int percent) {
        chatTextSize = clamp(chatTextSize + percent, 70, 150);
        save();
    }
    public void adjustSwipeSensitivity(int percent) {
        swipeSensitivity = clamp(swipeSensitivity + percent, 50, 160);
        save();
    }
    public void adjustKeyGap(int pixels) {
        keyGap = clamp(keyGap + pixels, 1, 5);
        save();
    }
    public void adjustLongPressMs(int milliseconds) {
        longPressMs = clamp(longPressMs + milliseconds, 250, 850);
        save();
    }
    public void resetAllVisualSettings() {
        setPosition(0, 0, 0);
        historyHeight = 0f;
        inputY = 0;
        keyboardSize = 100;
        keyboardTilt = -30;
        opacity = 100;
        chatTextSize = 100;
        swipeSensitivity = 100;
        keyGap = 2;
        longPressMs = 450;
        positionLocked = false;
        clickSound = false;
        wordSuggestions = true;
        autoCorrect = true;
        autoSpace = true;
        save();
    }

    public void toggleHaptics() { haptics = !haptics; save(); }
    public void adjustHapticStrength() {
        hapticStrength = hapticStrength >= 360 ? 120 : hapticStrength + 120;
        save();
    }
    public void toggleLeft() {
        if (leftHand && !rightHand) return;
        leftHand = !leftHand;
        save();
    }
    public void toggleRight() {
        if (rightHand && !leftHand) return;
        rightHand = !rightHand;
        save();
    }
    public void adjustBeam(int centimetres) {
        beamCentimetres = clamp(beamCentimetres + centimetres, 18, 70);
        save();
    }
    public void adjustDot(int pixels) {
        dotRadius = clamp(dotRadius + pixels, 1, 5);
        save();
    }
    public void move(float right, float down, float forward) {
        setPosition(offsetRight + right, offsetDown + down, offsetForward + forward);
    }
    public void setPosition(float right, float down, float forward) {
        // Metres in room coordinates; never teleport a keyboard out of reach.
        offsetRight = clamp(right, -0.65f, 0.65f);
        offsetDown = clamp(down, -0.55f, 0.55f);
        offsetForward = clamp(forward, -0.45f, 0.45f);
    }
    public void savePosition() { save(); }
    public void resetPosition() {
        setPosition(0, 0, 0);
        historyHeight = 0f;
        inputY = 0;
        save();
    }

    private void load() {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            p.load(in);
            haptics = Boolean.parseBoolean(p.getProperty("haptics", "true"));
            hapticStrength = clamp(asInt(p, "hapticStrength", 200), 120, 360);
            leftHand = Boolean.parseBoolean(p.getProperty("leftHand", "true"));
            rightHand = Boolean.parseBoolean(p.getProperty("rightHand", "true"));
            if (!leftHand && !rightHand) rightHand = true;
            beamCentimetres = clamp(asInt(p, "beamCentimetres", 38), 18, 70);
            dotRadius = clamp(asInt(p, "dotRadius", 2), 1, 5);
            setPosition(asFloat(p, "right", 0),
                asFloat(p, "down", 0), asFloat(p, "forward", 0));
            historyHeight = clamp(asFloat(p, "historyHeight", 0), -0.35f, 0.35f);
            inputY = clamp(asInt(p, "inputY", 0), -90, 90);
            keyboardSize = clamp(asInt(p, "keyboardSize", 100), 70, 140);
            keyboardTilt = clamp(asInt(p, "keyboardTilt", -30), -65, 20);
            opacity = clamp(asInt(p, "opacity", 100), 30, 100);
            chatTextSize = clamp(asInt(p, "chatTextSize", 100), 70, 150);
            swipeSensitivity = clamp(asInt(p, "swipeSensitivity", 100), 50, 160);
            keyGap = clamp(asInt(p, "keyGap", 2), 1, 5);
            longPressMs = clamp(asInt(p, "longPressMs", 450), 250, 850);
            positionLocked = Boolean.parseBoolean(p.getProperty("positionLocked", "false"));
            clickSound = Boolean.parseBoolean(p.getProperty("clickSound", "false"));
            wordSuggestions = Boolean.parseBoolean(p.getProperty("wordSuggestions", "true"));
            autoCorrect = Boolean.parseBoolean(p.getProperty("autoCorrect", "true"));
            autoSpace = Boolean.parseBoolean(p.getProperty("autoSpace", "true"));
        } catch (IOException ignored) {
            // First run or read-only Android configuration: keep defaults.
        }
    }

    public void save() {
        Properties p = new Properties();
        p.setProperty("haptics", Boolean.toString(haptics));
        p.setProperty("hapticStrength", Integer.toString(hapticStrength));
        p.setProperty("leftHand", Boolean.toString(leftHand));
        p.setProperty("rightHand", Boolean.toString(rightHand));
        p.setProperty("beamCentimetres", Integer.toString(beamCentimetres));
        p.setProperty("dotRadius", Integer.toString(dotRadius));
        p.setProperty("right", Float.toString(offsetRight));
        p.setProperty("down", Float.toString(offsetDown));
        p.setProperty("forward", Float.toString(offsetForward));
        p.setProperty("historyHeight", Float.toString(historyHeight));
        p.setProperty("inputY", Integer.toString(inputY));
        p.setProperty("keyboardSize", Integer.toString(keyboardSize));
        p.setProperty("keyboardTilt", Integer.toString(keyboardTilt));
        p.setProperty("opacity", Integer.toString(opacity));
        p.setProperty("chatTextSize", Integer.toString(chatTextSize));
        p.setProperty("swipeSensitivity", Integer.toString(swipeSensitivity));
        p.setProperty("keyGap", Integer.toString(keyGap));
        p.setProperty("longPressMs", Integer.toString(longPressMs));
        p.setProperty("positionLocked", Boolean.toString(positionLocked));
        p.setProperty("clickSound", Boolean.toString(clickSound));
        p.setProperty("wordSuggestions", Boolean.toString(wordSuggestions));
        p.setProperty("autoCorrect", Boolean.toString(autoCorrect));
        p.setProperty("autoSpace", Boolean.toString(autoSpace));
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream out = Files.newOutputStream(path)) {
                p.store(out, "FoxCraft VR keyboard accessibility settings");
            }
        } catch (IOException ignored) {
            // The current VR session still gets the requested settings.
        }
    }

    private static int asInt(Properties p, String key, int fallback) {
        try { return Integer.parseInt(p.getProperty(key, "")); }
        catch (NumberFormatException e) { return fallback; }
    }
    private static float asFloat(Properties p, String key, float fallback) {
        try {
            float f = Float.parseFloat(p.getProperty(key, ""));
            return Float.isFinite(f) ? f : fallback;
        } catch (NumberFormatException e) { return fallback; }
    }
    private static int clamp(int n, int low, int high) {
        return Math.max(low, Math.min(high, n));
    }
    private static float clamp(float n, float low, float high) {
        return Math.max(low, Math.min(high, n));
    }
}

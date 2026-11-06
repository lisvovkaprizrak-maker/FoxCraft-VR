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

    public void toggleHaptics() { haptics = !haptics; save(); }
    public void adjustHapticStrength() {
        hapticStrength = hapticStrength >= 360 ? 120 : hapticStrength + 120;
        save();
    }
    public void toggleLeft() {
        if (!leftHand && !rightHand) rightHand = true;
        leftHand = !leftHand;
        save();
    }
    public void toggleRight() {
        if (!leftHand && !rightHand) leftHand = true;
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

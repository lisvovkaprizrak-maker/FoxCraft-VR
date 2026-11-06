package dev.foxcraft.keyboard;

import java.util.ArrayList;
import java.util.List;

/**
 * Captures a stroke, not the characters of the keys it happens to intersect.
 * Only a dictionary word is committed; a path cannot dump accidental letters.
 * The gesture remains attached to one VR hand and one focused input field.
 */
public final class SwipeGesture {
    private static final int MAX_POINTS = 192;
    private static final double SAMPLE_STEP = 5.0;
    private static final double MIN_DISTANCE = 13.0;
    private final List<GestureWordRecognizer.Point> points = new ArrayList<>();
    private KeyboardLayout.Key first;
    private KeyboardLayout.Key lastLetter;
    private KeyboardLayout.Panel panel;
    private double previousX;
    private double previousY;
    private double travel;
    private int crossed;
    private int rankedAt = -1;
    private List<String> cachedWords = List.of();

    public void begin(KeyboardLayout.Key key, double x, double y) {
        cancel();
        if (isLetter(key)) {
            first = key;
            lastLetter = key;
            crossed = 1;
            points.add(new GestureWordRecognizer.Point(x, y));
            previousX = x;
            previousY = y;
        }
    }

    public void move(KeyboardLayout.Panel current, double x, double y) {
        if (first == null || current == null) return;
        panel = current;
        double dx = x - previousX, dy = y - previousY;
        double distance = Math.hypot(dx, dy);
        if (distance < 0.01) return;
        travel += distance;
        int steps = Math.min(256, Math.max(1, (int) Math.ceil(distance / SAMPLE_STEP)));
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            double px = previousX + dx*t, py = previousY + dy*t;
            if (points.size() >= MAX_POINTS) {
                // Keep the whole shape, not just its beginning, on long gestures.
                for (int j = points.size()-2; j > 0; j -= 2) points.remove(j);
                rankedAt = -1;
            }
            points.add(new GestureWordRecognizer.Point(px, py));
            KeyboardLayout.Key hit = current.at(px, py);
            if (isLetter(hit) && hit != lastLetter) {
                lastLetter = hit;
                crossed++;
            }
        }
        previousX = x;
        previousY = y;
    }

    public boolean isSwiping() {
        return first != null && crossed > 1 && travel >= MIN_DISTANCE;
    }

    /** Up to three approximate word matches, with no raw intermediate letters. */
    public List<String> suggestions() {
        if (!isSwiping() || panel == null) return List.of();
        if (rankedAt < 0 || points.size()-rankedAt >= 5) {
            cachedWords = GestureWordRecognizer.suggest(panel, points, 3);
            rankedAt = points.size();
        }
        return cachedWords;
    }

    public String preview() {
        List<String> words = suggestions();
        return words.isEmpty() ? "" : words.get(0);
    }

    public KeyboardLayout.Key firstKey() { return first; }

    public String finish() {
        List<String> words = isSwiping()
            ? GestureWordRecognizer.suggest(panel, points, 3) : List.of();
        String best = words.isEmpty() ? "" : words.get(0);
        cancel();
        return best;
    }

    public void cancel() {
        first = null;
        lastLetter = null;
        panel = null;
        points.clear();
        travel = 0;
        crossed = 0;
        rankedAt = -1;
        cachedWords = List.of();
    }

    private static boolean isLetter(KeyboardLayout.Key key) {
        return key != null && key.command() == KeyboardLayout.Command.TYPE &&
            key.value().codePointCount(0, key.value().length()) == 1 &&
            Character.isLetter(key.value().codePointAt(0));
    }
}

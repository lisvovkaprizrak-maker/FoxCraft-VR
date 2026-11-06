package dev.foxcraft.keyboard;

/**
 * Collects letter keys crossed by a held controller ray. Does not use a
 * dictionary or network services: the preview is the actual sequence visited.
 * Swipe text is committed ONLY on trigger release, never during movement.
 */
public final class SwipeGesture {
    private static final int MAX_LETTERS = 48;
    private static final double SAMPLE_STEP_PX = 5.0;

    private final StringBuilder letters = new StringBuilder();
    private KeyboardLayout.Key first;
    private KeyboardLayout.Key last;
    private double previousX;
    private double previousY;
    private boolean hasPoint;

    public void begin(KeyboardLayout.Key key, double x, double y) {
        cancel();
        if (isLetter(key)) {
            first = key;
            last = key;
            letters.append(key.value());
            previousX = x;
            previousY = y;
            hasPoint = true;
        }
    }

    public void move(KeyboardLayout.Panel panel, double x, double y) {
        if (first == null || panel == null || !hasPoint) return;
        double dx = x - previousX;
        double dy = y - previousY;
        double distance = Math.hypot(dx, dy);
        // Sampling the whole segment also catches keys crossed between frames.
        int steps = Math.min(256, Math.max(1, (int) Math.ceil(distance / SAMPLE_STEP_PX)));
        for (int i = 1; i <= steps; ++i) {
            double fraction = (double) i / steps;
            visit(panel.at(previousX + fraction * dx, previousY + fraction * dy));
        }
        previousX = x;
        previousY = y;
    }

    private void visit(KeyboardLayout.Key key) {
        if (!isLetter(key) || key == last || letters.length() >= MAX_LETTERS)
            return;
        last = key;
        letters.append(key.value());
    }

    public boolean isSwiping() { return letters.codePointCount(0, letters.length()) >= 2; }

    public String preview() { return isSwiping() ? letters.toString() : ""; }

    public KeyboardLayout.Key firstKey() { return first; }

    public String finish() {
        String text = preview();
        cancel();
        return text;
    }

    public void cancel() {
        first = null;
        last = null;
        letters.setLength(0);
        hasPoint = false;
    }

    private static boolean isLetter(KeyboardLayout.Key key) {
        return key != null && key.command() == KeyboardLayout.Command.TYPE &&
            key.value().codePointCount(0, key.value().length()) == 1 &&
            Character.isLetter(key.value().codePointAt(0));
    }
}

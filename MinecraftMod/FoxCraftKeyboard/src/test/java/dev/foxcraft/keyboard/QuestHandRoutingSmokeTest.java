package dev.foxcraft.keyboard;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiPredicate;

/** Deterministic handed trigger routing; this does not simulate Quest hardware. */
public final class QuestHandRoutingSmokeTest {
    enum Hand { RIGHT, LEFT, UNKNOWN }

    public static void main(String[] args) {
        Object keyboard = new Object();
        Object guiLeft = new Object();
        Object guiRight = new Object();
        Object use = new Object();
        Set<Key> physical = new HashSet<>();
        BiPredicate<Object, String> read = (action, side) ->
            physical.contains(new Key(action, side));

        verify(!pressed(Hand.RIGHT, keyboard, guiLeft, guiRight, use, read),
            "Right hand starts released");
        verify(!pressed(Hand.LEFT, keyboard, guiLeft, guiRight, use, read),
            "Left hand starts released");

        // Reproduce the reported regression: only RIGHT physical trigger down.
        physical.add(new Key(guiLeft, "RIGHT"));
        verify(pressed(Hand.RIGHT, keyboard, guiLeft, guiRight, use, read),
            "Right index trigger presses RIGHT pointer");
        verify(!pressed(Hand.LEFT, keyboard, guiLeft, guiRight, use, read),
            "Right index trigger cannot press LEFT pointer");
        physical.clear();

        // LEFT index trigger may be bound to GUI right click or Minecraft Use.
        physical.add(new Key(guiRight, "LEFT"));
        verify(pressed(Hand.LEFT, keyboard, guiLeft, guiRight, use, read),
            "Left GUI action presses LEFT pointer");
        verify(!pressed(Hand.RIGHT, keyboard, guiLeft, guiRight, use, read),
            "Left GUI action cannot press RIGHT pointer");
        physical.clear();
        physical.add(new Key(use, "LEFT"));
        verify(pressed(Hand.LEFT, keyboard, guiLeft, guiRight, use, read),
            "Left Use action presses LEFT pointer");
        verify(!pressed(Hand.RIGHT, keyboard, guiLeft, guiRight, use, read),
            "Left Use cannot press RIGHT pointer");
        physical.clear();

        // The native keyboard action may be bound on either controller.
        physical.add(new Key(keyboard, "RIGHT"));
        verify(pressed(Hand.RIGHT, keyboard, guiLeft, guiRight, use, read),
            "Right keyboard action stays right");
        verify(!pressed(Hand.LEFT, keyboard, guiLeft, guiRight, use, read),
            "Right keyboard action does not mirror left");
        physical.clear();
        physical.add(new Key(keyboard, "LEFT"));
        verify(pressed(Hand.LEFT, keyboard, guiLeft, guiRight, use, read),
            "Left keyboard action stays left");
        verify(!pressed(Hand.RIGHT, keyboard, guiLeft, guiRight, use, read),
            "Left keyboard action does not mirror right");
        physical.clear();

        // Both hands held at once must not steal each other's states.
        physical.add(new Key(guiLeft, "RIGHT"));
        physical.add(new Key(guiRight, "LEFT"));
        verify(pressed(Hand.LEFT, keyboard, guiLeft, guiRight, use, read),
            "Simultaneous left trigger");
        verify(pressed(Hand.RIGHT, keyboard, guiLeft, guiRight, use, read),
            "Simultaneous right trigger");
        physical.clear();

        // These action/hand combinations should NOT be treated as clicks.
        physical.add(new Key(guiLeft, "LEFT"));
        physical.add(new Key(guiRight, "RIGHT"));
        physical.add(new Key(use, "RIGHT"));
        verify(!pressed(Hand.LEFT, keyboard, guiLeft, guiRight, use, read),
            "Left ray ignores wrong left-click action");
        verify(!pressed(Hand.RIGHT, keyboard, guiLeft, guiRight, use, read),
            "Right ray ignores left Use / opposite GUI action");
        verify(!pressed(Hand.UNKNOWN, keyboard, guiLeft, guiRight, use, read),
            "Unknown hand rejected");
        verify(!pressed(null, keyboard, guiLeft, guiRight, use, read),
            "Missing hand rejected");

        verify(!FoxCraftQuestOpenXrInput.handles(new Object()),
            "Other Vivecraft providers use their original origin-based route");
        System.out.println("FoxCraft Quest/OpenXR per-hand trigger routing: OK");
    }

    private record Key(Object binding, String hand) {}

    private static boolean pressed(Object hand, Object keyboard, Object guiLeft,
                                   Object guiRight, Object use,
                                   BiPredicate<Object, String> read) {
        return FoxCraftQuestOpenXrInput.pressedForHand(
            hand, keyboard, guiLeft, guiRight, use, read);
    }

    private static void verify(boolean condition, String why) {
        if (!condition) throw new AssertionError(why);
    }
}

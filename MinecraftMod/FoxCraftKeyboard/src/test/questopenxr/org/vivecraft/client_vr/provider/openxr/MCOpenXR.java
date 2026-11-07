package org.vivecraft.client_vr.provider.openxr;
import org.lwjgl.openxr.XrInstance;
import org.lwjgl.openxr.XrSession;
import java.util.HashMap;
import java.util.Map;
/** Mimics the QuestCraft class name and public OpenXR handles. */
public final class MCOpenXR {
    public final XrInstance instance = new XrInstance();
    public final XrSession session = new XrSession();
    private final Map<ActionSet,Long> actionSetHandles = new HashMap<>();
    public MCOpenXR() { actionSetHandles.put(ActionSet.GUI, 123L);
        actionSetHandles.put(ActionSet.KEYBOARD, 456L); }
    public enum ActionSet { GUI, KEYBOARD }
    public static final class Action {
        public final long handle;
        public final ActionSet actionSet;
        public Action(long h, ActionSet set) { handle = h; actionSet = set; }
    }
    public static final class Binding {
        public final Action action;
        public Binding(long h, ActionSet set) { action = new Action(h, set); }
    }
    public Action getInputAction(Binding binding) { return binding.action; }
    public long getPath(String path) { return path.contains("/right") ? 200L : 100L; }
}

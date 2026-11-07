package org.lwjgl.openxr;
import java.util.HashSet;
import java.util.Set;
public final class XrSession {
    public final Set<Long> active = new HashSet<>();
    public final Set<String> pressed = new HashSet<>();
    public void press(long action, long hand) { active.add(action); pressed.add(action + ":" + hand); }
    public void release(long action, long hand) { pressed.remove(action + ":" + hand); }
}

package dev.foxcraft.keyboard;

import org.vivecraft.client_vr.provider.openxr.MCOpenXR;
import org.vivecraft.client_vr.provider.openxr.MCOpenXR.ActionSet;
import org.vivecraft.client_vr.provider.openxr.MCOpenXR.Binding;
import org.vivecraft.client_vr.provider.ControllerType;

/** Tests physical OpenXR hand paths, not Vivecraft's faulty cached origin. */
public final class QuestOpenXrTriggerSmokeTest {
    private static void require(boolean ok, String note) {
        if (!ok) throw new AssertionError(note);
    }
    public static void main(String[] args) {
        MCOpenXR provider = new MCOpenXR();
        Binding keyboard = new Binding(11L, ActionSet.KEYBOARD);
        Binding gui = new Binding(22L, ActionSet.GUI);
        require(FoxCraftQuestOpenXrInput.handles(provider), "Quest OpenXR recognized");
        // RIGHT physical index trigger fires RIGHT cursor only, although
        // QuestCraft's legacy getOriginControllerType reports LEFT for it.
        provider.session.press(22L, 200L);
        require(FoxCraftQuestOpenXrInput.pressed(provider, ControllerType.RIGHT,
            keyboard, gui), "RIGHT GUI trigger activates RIGHT pointer");
        require(!FoxCraftQuestOpenXrInput.pressed(provider, ControllerType.LEFT,
            keyboard, gui), "RIGHT GUI trigger must not activate LEFT pointer");
        provider.session.release(22L, 200L);
        // Once KEYBOARD action set is active (via optional mixin), the native
        // keyboard action exposes both physical subactions independently.
        provider.session.press(11L, 100L);
        require(FoxCraftQuestOpenXrInput.pressed(provider, ControllerType.LEFT,
            keyboard, gui), "LEFT native trigger activates LEFT pointer");
        require(!FoxCraftQuestOpenXrInput.pressed(provider, ControllerType.RIGHT,
            keyboard, gui), "LEFT native trigger must not activate RIGHT pointer");
        provider.session.release(11L, 100L);
        provider.session.press(11L, 200L);
        require(FoxCraftQuestOpenXrInput.pressed(provider, ControllerType.RIGHT,
            keyboard, gui), "RIGHT native trigger activates RIGHT pointer");
        require(!FoxCraftQuestOpenXrInput.pressed(provider, ControllerType.LEFT,
            keyboard, gui), "RIGHT native trigger never activates LEFT pointer");
        provider.session.release(11L, 200L);
        // GUI LEFT click is bound only to physical RIGHT controller on Quest.
        provider.session.press(22L, 100L);
        require(!FoxCraftQuestOpenXrInput.pressed(provider, ControllerType.LEFT,
            keyboard, gui), "GUI fallback never allows false LEFT trigger");
        provider.session.release(22L, 100L);
        require(!FoxCraftQuestOpenXrInput.pressed(provider, ControllerType.RIGHT,
            keyboard, gui), "Released trigger cannot fire");
        System.out.println("QuestCraft OpenXR physical left/right trigger tests: OK");
    }
}

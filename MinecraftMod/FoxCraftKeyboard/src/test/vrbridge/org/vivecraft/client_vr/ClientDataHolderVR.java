package org.vivecraft.client_vr;

/** Fake Vivecraft API for offline bridge tests only; not packaged with mod. */
public final class ClientDataHolderVR {
    public static final ClientDataHolderVR INSTANCE = new ClientDataHolderVR();
    public final FakeVR vr = new FakeVR();
    public final FakePlayer vrPlayer = new FakePlayer();

    public static ClientDataHolderVR getInstance() { return INSTANCE; }

    public static final class FakeVR {
        public boolean tracking = true;
        public boolean isControllerTracking(int controller) {
            return tracking && controller == 1;
        }
        public FakeAction getInputAction(Object binding) {
            if (binding instanceof org.vivecraft.client_vr.gameplay.screenhandlers.GuiHandler.FakeGuiClick click)
                return click.action;
            return null;
        }
        protected org.vivecraft.client_vr.provider.ControllerType getOriginControllerType(long origin) {
            return switch ((int) origin) {
                case 100 -> org.vivecraft.client_vr.provider.ControllerType.LEFT;
                case 200 -> org.vivecraft.client_vr.provider.ControllerType.RIGHT;
                default -> null;
            };
        }
    }

    public static final class FakeAction {
        public boolean active;
        public boolean pressed;
        public long origin;
        public boolean isActive() { return active; }
        public boolean isButtonPressed() { return pressed; }
        public long getLastOrigin() { return origin; }
    }

    public static final class FakePlayer {
        public final FakeVRData vrdata_room_pre = new FakeVRData();
    }

    public static final class FakeVRData {
        public Object getController(int id) {
            return id == 1 ? this : null;
        }
    }
}

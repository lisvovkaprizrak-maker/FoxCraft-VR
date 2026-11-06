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

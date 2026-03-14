package com.flowingsun.tacticalmap.util;

public class SyncActionGenerator {
    private static final int SYNC_ACTION_BITS = calculateBits(SyncAction.values().length);
    private static final int SIDE_FLAG_BITS = calculateBits(SideFlag.values().length);

    private static final int SYNC_ACTION_MASK = (1 << SYNC_ACTION_BITS) - 1;
    private static final int SIDE_FLAG_MASK = (1 << SIDE_FLAG_BITS) - 1;

    @Deprecated
    public static byte generate(SyncAction syncAction) {
        return generate(syncAction, SideFlag.UNDEFINED);
    }

    public static byte generate(SyncAction syncAction, SideFlag sideFlag) {
        return (byte) ((syncAction.ordinal() & SYNC_ACTION_MASK) | (sideFlag.ordinal() & SIDE_FLAG_MASK) << SYNC_ACTION_BITS);
    }

    public static SyncAction getSyncAction(Byte syncAction) {
        int index = syncAction & SYNC_ACTION_MASK;
        if (index >= SyncAction.values().length) {
            throw new IllegalArgumentException("Invalid SyncAction index: " + index);
        }
        return SyncAction.values()[index];
    }

    public static SideFlag getSideFlag(Byte syncAction) {
        int index = (syncAction >> SYNC_ACTION_BITS) & SIDE_FLAG_MASK;
        if (index >= SideFlag.values().length) {
            throw new IllegalArgumentException("Invalid SideFlag index: " + index);
        }
        return SideFlag.values()[index];
    }

    private static int calculateBits(int count) {
        if (count <= 0) {
            return 0;
        }
        return 32 - Integer.numberOfLeadingZeros(count - 1);
    }

    public enum SyncAction {
        ADD,
        DELETE
    }

    public enum SideFlag {
        C2S,
        S2C,
        UNDEFINED
    }
}

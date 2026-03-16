package com.flowingsun.tacticalmap.network;

import com.flowingsun.tacticalmap.TacticalMap;
import com.flowingsun.tacticalmap.util.SyncActionGenerator;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.client.waypoint.Waypoint;
import dev.ftb.mods.ftbchunks.client.map.WaypointImpl;
import dev.ftb.mods.ftbchunks.client.map.WaypointManagerImpl;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/* class update plan
 * 1. Use BitSet instead of long to send data
 */
public class WaypointSyncPacket {
    private static final int ACTION_BITS = SyncActionGenerator.TOTAL_BITS;
    private static final int ACTION_MASK = SyncActionGenerator.TOTAL_MASK;
    private static final int COLOR_MASK = ~ACTION_MASK;

    /*
     * If you edited this file, you need to bump LOCAL_PROTOCOL_VERSION_STRING
     * Version String should adapt this way:
     * Major.minor.patch
     * When major version updated, the protocol should fully unsupported with old version
     * When minor version updated, the protocol should be something compatible with old version, but not fully supported with new version
     * When patch version updated, the protocol should be fully compatible with old version, but something like text or implementation should be changed
     */
    private static final String LOCAL_PROTOCOL_VERSION_STRING = "1.0.2";
    private static final IntList LOCAL_PROTOCOL_VERSION = createProtocolVersion(LOCAL_PROTOCOL_VERSION_STRING); // if you edited this file, you need to bump PROTOCOL_VERSION

    private final String name;
    private final BlockPos pos;
    private final int color;
    private final SyncActionGenerator.SyncAction syncAction;
    private final SyncActionGenerator.SideFlag sideFlag;
    private final IntList protocolVersion;
    private FunctionEnable functionEnable = FunctionEnable.UNDEFINE;

    public WaypointSyncPacket(Waypoint wp, byte action) {
        this(wp.getName(), wp.getPos(), ((long) wp.getColor() << ACTION_BITS) | action);
    }

    public WaypointSyncPacket(String name, long posLong, long data) {
        this(name, BlockPos.of(posLong), data);
    }

    public WaypointSyncPacket(FriendlyByteBuf buf) {
        this.protocolVersion = buf.readIntIdList();
        verifyProtocolVersion();
        String name = null;
        BlockPos pos = null;
        int color = -1;
        SyncActionGenerator.SyncAction syncAction = null;
        SyncActionGenerator.SideFlag sideFlag = null;
        if (!this.functionEnable.equals(FunctionEnable.NONE)) { // 完全不兼容时，直接禁用，不要读取，防止报错
            try {
                name = buf.readUtf();
                pos = BlockPos.of(buf.readLong());
                long data = buf.readLong();
                color = (int) (data >> ACTION_BITS);
                byte actionByte = (byte) (data & ACTION_MASK);
                syncAction = SyncActionGenerator.getSyncAction(actionByte);
                sideFlag = SyncActionGenerator.getSideFlag(actionByte);
            } catch (Exception e) {
                if (functionEnable.equals(FunctionEnable.FULL)) {
                    throw e;
                }
            }
        }
        this.name = name;
        this.pos = pos;
        this.color = color;
        this.syncAction = syncAction;
        this.sideFlag = sideFlag;
    }

    public WaypointSyncPacket(String name, BlockPos pos, long data) {
        this(LOCAL_PROTOCOL_VERSION, name, pos, data);
    }

    public WaypointSyncPacket(IntList protocolVersion, String name, BlockPos pos, long data) {
        this.name = name;
        this.pos = pos;
        this.color = (int) (data >> ACTION_BITS);
        byte actionByte = (byte) (data & ACTION_MASK);
        this.syncAction = SyncActionGenerator.getSyncAction(actionByte);
        this.sideFlag = SyncActionGenerator.getSideFlag(actionByte);
        this.protocolVersion = protocolVersion;
    }

    public WaypointSyncPacket(String name, BlockPos pos, byte syncAction, int color) {
        this(name, pos, SyncActionGenerator.getSyncAction(syncAction), SyncActionGenerator.getSideFlag(syncAction), color);
    }

    public WaypointSyncPacket(String name, BlockPos pos, SyncActionGenerator.SyncAction syncAction, SyncActionGenerator.SideFlag sideFlag, int color) {
        this.name = name;
        this.pos = pos;
        this.syncAction = syncAction;
        this.sideFlag = sideFlag;
        this.color = color;
        this.protocolVersion = LOCAL_PROTOCOL_VERSION;
        this.functionEnable = FunctionEnable.FULL; // local function, so no need to check
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeIntIdList(this.protocolVersion);
        buf.writeUtf(this.name);
        buf.writeLong(this.pos.asLong());
        buf.writeLong(this.encodeData());
    }

    public WaypointSyncPacket transformToS2C() {
        return new WaypointSyncPacket(this.name, this.pos, this.syncAction, SyncActionGenerator.SideFlag.S2C, this.color);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        if (functionEnable.equals(FunctionEnable.UNDEFINE)) {
            verifyProtocolVersion();
        }
        if (functionEnable.equals(FunctionEnable.NONE) || this.pos == null) return; // 不兼容时，直接禁用
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (this.sideFlag.equals(SyncActionGenerator.SideFlag.C2S)) {
                if (sender != null) {
                    TacticalMap.broadcastToTeammates(sender, this.transformToS2C());
                }
            } else if (this.sideFlag.equals(SyncActionGenerator.SideFlag.S2C)) {
                FTBChunksAPI.clientApi().getWaypointManager().ifPresent(manager -> {
                    if (!(manager instanceof WaypointManagerImpl impl)) return;

                    TacticalMap.IS_SYNCING.set(true);
                    try {
                        if (this.syncAction == SyncActionGenerator.SyncAction.ADD) {
                            // 【修正逻辑】：
                            // 1. 调用 addWaypointAt 创建并自动添加路径点（它内部会处理所有复杂的构造参数）
                            Waypoint wp = impl.addWaypointAt(this.pos, this.name == null ? "null" : this.name);

                            // 2. 转换为实现类设置颜色（源码显示 setColor 返回 WaypointImpl，支持链式调用）
                            if (wp instanceof WaypointImpl wpImpl && this.color != -1) {
                                wpImpl.setColor(color);
                                // 3. 必须手动刷新图标，否则地图上显示的还是默认颜色
                                wpImpl.refreshIcon();
                            }
                        } else if (this.syncAction == SyncActionGenerator.SyncAction.DELETE) {
                            for (Waypoint waypoint : manager.getAllWaypoints()) {
                                if (waypoint.getPos().atY(0).equals(this.pos.atY(0))) {
                                    manager.removeWaypoint(waypoint);
                                    break;
                                }
                            }
                        }
                    } finally {
                        TacticalMap.IS_SYNCING.set(false);
                    }
                });
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public String getName() {
        return name;
    }

    private long encodeData() {
        return ((long) this.color << ACTION_BITS) | SyncActionGenerator.generate(syncAction, sideFlag);
    }

    private static IntList createProtocolVersion(String version) {
        String[] parts = version.split("\\.");
        int[] intParts = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                intParts[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException e) {
                TacticalMap.LOGGER.error("Invalid version number: {}", parts[i], e);
                intParts[i] = 0;
            }
        }
        return IntList.of(intParts);
    }

    private void verifyProtocolVersion() {
        if (this.protocolVersion.equals(LOCAL_PROTOCOL_VERSION)) {
            this.functionEnable = FunctionEnable.FULL;
            return;
        }
        for (int i = 0; i < 3; i++) {
            int local = this.protocolVersion.getInt(i);
            int remote = LOCAL_PROTOCOL_VERSION.getInt(i);
            if (local != remote) {
                switch (i) {
                    case 0 -> this.functionEnable = FunctionEnable.NONE;
                    case 1 -> this.functionEnable = FunctionEnable.PARTIAL;
                    case 2 -> this.functionEnable = FunctionEnable.FULL;
                }
                return;
            }
        }
        this.functionEnable = FunctionEnable.FULL;
    }

    private enum FunctionEnable {
        FULL,
        PARTIAL,
        NONE,
        UNDEFINE
    }
}
package com.flowingsun.tacticalmap.network;

import com.flowingsun.tacticalmap.TacticalMap;
import com.flowingsun.tacticalmap.util.SyncActionGenerator;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.client.waypoint.Waypoint;
import dev.ftb.mods.ftbchunks.client.map.WaypointImpl;
import dev.ftb.mods.ftbchunks.client.map.WaypointManagerImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class WaypointSyncPacket {
    // --- 协议常量定义 ---
    private static final int ACTION_BITS = 8;
    private static final long ACTION_MASK = 0xFFL;
    private static final long COLOR_MASK = ~ACTION_MASK;

    private final String name;
    private final long posLong;
    private final long data;

    public WaypointSyncPacket(Waypoint wp, byte action) {
        this.name = wp.getName();
        this.posLong = wp.getPos().asLong();
        this.data = ((long) wp.getColor() << ACTION_BITS) | (action & ACTION_MASK);
    }

    public WaypointSyncPacket(String name, long posLong, long data) {
        this.name = name;
        this.posLong = posLong;
        this.data = data;
    }

    public WaypointSyncPacket(FriendlyByteBuf buf) {
        this.name = buf.readUtf();
        this.posLong = buf.readLong();
        this.data = buf.readLong();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(name);
        buf.writeLong(posLong);
        buf.writeLong(data);
    }

    public WaypointSyncPacket transformToS2C() {
        byte oldAction = (byte) (this.data & ACTION_MASK);
        SyncActionGenerator.SyncAction actionType = SyncActionGenerator.getSyncAction(oldAction);
        byte newAction = SyncActionGenerator.generate(actionType, SyncActionGenerator.SideFlag.S2C);
        long newData = (this.data & COLOR_MASK) | (newAction & ACTION_MASK);
        return new WaypointSyncPacket(this.name, this.posLong, newData);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            byte actionByte = (byte) (this.data & ACTION_MASK);
            int color = (int) (this.data >> ACTION_BITS);
            BlockPos pos = BlockPos.of(this.posLong);

            ServerPlayer sender = ctx.get().getSender();
            if (SyncActionGenerator.getSideFlag(actionByte).equals(SyncActionGenerator.SideFlag.C2S)) {
                if (sender != null) {
                    TacticalMap.broadcastToTeammates(sender, this.transformToS2C());
                }
            } else {
                FTBChunksAPI.clientApi().getWaypointManager().ifPresent(manager -> {
                    if (!(manager instanceof WaypointManagerImpl impl)) return;

                    TacticalMap.IS_SYNCING.set(true);
                    try {
                        SyncActionGenerator.SyncAction type = SyncActionGenerator.getSyncAction(actionByte);
                        if (type == SyncActionGenerator.SyncAction.ADD) {
                            // 【修正逻辑】：
                            // 1. 调用 addWaypointAt 创建并自动添加路径点（它内部会处理所有复杂的构造参数）
                            Waypoint wp = impl.addWaypointAt(pos, this.name);

                            // 2. 转换为实现类设置颜色（源码显示 setColor 返回 WaypointImpl，支持链式调用）
                            if (wp instanceof WaypointImpl wpImpl) {
                                wpImpl.setColor(color);
                                // 3. 必须手动刷新图标，否则地图上显示的还是默认颜色
                                wpImpl.refreshIcon();
                            }
                        } else if (type == SyncActionGenerator.SyncAction.DELETE) {
                            // 删除逻辑保持使用 stream 匹配，因为 removeWaypointAt 同样需要 mapDimension 构造对象
                            impl.getAllWaypoints().stream()
                                    .filter(w -> w.getPos().atY(0).equals(pos.atY(0)))
                                    .findFirst()
                                    .ifPresent(impl::removeWaypoint);
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
}
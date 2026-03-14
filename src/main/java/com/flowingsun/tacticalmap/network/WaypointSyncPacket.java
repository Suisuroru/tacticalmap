package com.flowingsun.tacticalmap.network;

import com.flowingsun.tacticalmap.TacticalMap;
import com.flowingsun.tacticalmap.util.SyncActionGenerator;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.client.waypoint.Waypoint;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class WaypointSyncPacket {
    private final byte action;
    private final String name;
    private final BlockPos pos;

    public WaypointSyncPacket(Waypoint wp, byte action) {
        this.action = action;
        this.name = wp.getName();
        this.pos = wp.getPos();
    }

    public WaypointSyncPacket(String name, BlockPos pos, byte action) {
        this.action = action;
        this.name = name;
        this.pos = pos;
    }

    public WaypointSyncPacket(FriendlyByteBuf buf) {
        this.action = buf.readByte();
        this.name = buf.readUtf();
        this.pos = buf.readBlockPos();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(action);
        buf.writeUtf(name);
        buf.writeBlockPos(pos);
    }

    public WaypointSyncPacket transformToS2C() {
        return new WaypointSyncPacket(this.name, this.pos, SyncActionGenerator.generate(SyncActionGenerator.getSyncAction(this.action), SyncActionGenerator.SideFlag.S2C));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (SyncActionGenerator.getSideFlag(this.action).equals(SyncActionGenerator.SideFlag.C2S)) {
                // 服务端逻辑：转发给队友
                if (sender != null) {
                    TacticalMap.broadcastToTeammates(sender, this.transformToS2C());
                } else {
                    TacticalMap.LOGGER.warn("[TacticalMap] 路径点[{}]({},{})同步数据包的发送者不存在", this.name, this.pos.getX(), this.pos.getZ());
                }
            } else if (SyncActionGenerator.getSideFlag(this.action).equals(SyncActionGenerator.SideFlag.S2C)) {
                // 客户端逻辑：实时应用更改
                FTBChunksAPI.clientApi().getWaypointManager().ifPresent(manager -> {
                    // 开启同步标志，防止 Mixin 再次拦截导致无限发包
                    TacticalMap.IS_SYNCING.set(true);
                    try {
                        if (SyncActionGenerator.getSyncAction(this.action).equals(SyncActionGenerator.SyncAction.ADD)) {
                            manager.addWaypointAt(this.pos, this.name);
                        } else if (SyncActionGenerator.getSyncAction(this.action).equals(SyncActionGenerator.SyncAction.DELETE)) {
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
}

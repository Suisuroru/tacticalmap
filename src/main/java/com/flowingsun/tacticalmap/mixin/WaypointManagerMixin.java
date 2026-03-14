package com.flowingsun.tacticalmap.mixin;

import com.flowingsun.tacticalmap.TacticalMap;
import com.flowingsun.tacticalmap.network.WaypointSyncPacket;
import com.flowingsun.tacticalmap.util.SyncActionGenerator;
import dev.ftb.mods.ftbchunks.client.map.WaypointImpl;
import dev.ftb.mods.ftbchunks.client.map.WaypointManagerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WaypointManagerImpl.class, remap = false)
public class WaypointManagerMixin {

    @Inject(method = "add", at = @At("RETURN"))
    private void onAddWaypoint(WaypointImpl waypoint, CallbackInfo ci) {
        if (!TacticalMap.IS_SYNCING.get()) {
            TacticalMap.LOGGER.info("[TacticalMap] 检测到新路径点创建: {}", waypoint.getName());
            TacticalMap.CHANNEL.sendToServer(new WaypointSyncPacket(waypoint, SyncActionGenerator.generate(SyncActionGenerator.SyncAction.ADD, SyncActionGenerator.SideFlag.C2S)));
        }
    }

    @Inject(method = "remove", at = @At("RETURN"))
    private void onRemoveWaypoint(WaypointImpl waypoint, CallbackInfo ci) {
        if (!TacticalMap.IS_SYNCING.get()) {
            TacticalMap.LOGGER.info("[TacticalMap] 检测到路径点删除: {}", waypoint.getName());
            TacticalMap.CHANNEL.sendToServer(new WaypointSyncPacket(waypoint, SyncActionGenerator.generate(SyncActionGenerator.SyncAction.ADD, SyncActionGenerator.SideFlag.C2S)));
        }
    }
}

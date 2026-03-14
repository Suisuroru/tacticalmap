package com.flowingsun.tacticalmap.mixin;

import com.flowingsun.tacticalmap.TacticalMap;
import com.flowingsun.tacticalmap.network.WaypointSyncPacket;
import com.flowingsun.tacticalmap.util.SyncActionGenerator;
import dev.ftb.mods.ftbchunks.api.client.waypoint.Waypoint;
import dev.ftb.mods.ftbchunks.client.map.WaypointManagerImpl;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WaypointManagerImpl.class, remap = false)
public class WaypointManagerMixin {

    /**
     * 拦截添加路径点：玩家在地图上点击添加时调用此方法
     * 目标方法签名：Waypoint addWaypointAt(BlockPos pos, String name)
     */
    @Inject(method = "addWay" +
            "pointAt", at = @At("RETURN"))
    private void onAddWaypoint(BlockPos pos, String name, CallbackInfoReturnable<Waypoint> cir) {
        Waypoint waypoint = cir.getReturnValue();

        // 只有当路径点创建成功，且当前不是由同步逻辑触发时，才发送给服务器
        if (waypoint != null && !TacticalMap.IS_SYNCING.get()) {
            TacticalMap.LOGGER.info("[TacticalMap] 检测到新路径点创建: {}", name);
            TacticalMap.CHANNEL.sendToServer(new WaypointSyncPacket(waypoint, SyncActionGenerator.generate(SyncActionGenerator.SyncAction.ADD)));
        }
    }

    /**
     * 拦截删除路径点
     * 目标方法签名：boolean removeWaypoint(Waypoint waypoint)
     */
    @Inject(method = "removeWaypoint", at = @At("RETURN"))
    private void onRemoveWaypoint(Waypoint waypoint, CallbackInfoReturnable<Boolean> cir) {
        // 如果删除成功（返回 true），且不是同步触发
        if (cir.getReturnValue() && !TacticalMap.IS_SYNCING.get()) {
            TacticalMap.LOGGER.info("[TacticalMap] 检测到路径点删除: {}", waypoint.getName());
            TacticalMap.CHANNEL.sendToServer(new WaypointSyncPacket(waypoint, SyncActionGenerator.generate(SyncActionGenerator.SyncAction.ADD)));
        }
    }
}

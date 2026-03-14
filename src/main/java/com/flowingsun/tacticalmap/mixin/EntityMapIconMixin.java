package com.flowingsun.tacticalmap.mixin;

import com.flowingsun.tacticalmap.util.TeamUtils;
import dev.ftb.mods.ftbchunks.api.client.icon.MapType;
import dev.ftb.mods.ftbchunks.client.mapicon.EntityMapIcon;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityMapIcon.class, remap = false)
public abstract class EntityMapIconMixin {
    @Shadow
    @Final
    private Entity entity;

    @Inject(method = "isVisible", at = @At("HEAD"), cancellable = true)
    private void onIsVisible(MapType mapType, double distanceToPlayer, boolean outsideVisibleArea, CallbackInfoReturnable<Boolean> cir) {
        Player localPlayer = Minecraft.getInstance().player;
        if (localPlayer == null || this.entity == null) return;

        if (this.entity instanceof Player targetPlayer) {
            if (targetPlayer == localPlayer) return;
            // 如果不是队友，直接不渲染图标
            if (!TeamUtils.areOnSameTeam(localPlayer, targetPlayer)) {
                cir.setReturnValue(false);
            }
        }
    }
}
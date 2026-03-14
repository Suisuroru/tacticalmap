package com.flowingsun.tacticalmap.util;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.client.KnownClientPlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;
import java.util.UUID;

public class TeamUtils {

    public static boolean areOnSameTeam(Player p1, Player p2) {
        if (p1 == null || p2 == null) return false;

        // 1. 服务端判定逻辑 (用于数据包转发)
        if (p1 instanceof ServerPlayer sp1 && p2 instanceof ServerPlayer sp2) {
            try {
                // 修正：使用 getTeamForPlayerID 并传入玩家的 UUID
                // 修正：Team 对象的方法是 getId()
                UUID team1Id = FTBTeamsAPI.api().getManager()
                        .getTeamForPlayerID(sp1.getUUID())
                        .map(Team::getId)
                        .orElse(null);

                UUID team2Id = FTBTeamsAPI.api().getManager()
                        .getTeamForPlayerID(sp2.getUUID())
                        .map(Team::getId)
                        .orElse(null);

                return team1Id != null && team1Id.equals(team2Id);
            } catch (Exception e) {
                return false;
            }
        }

        // 2. 客户端判定逻辑 (用于雷达显示)
        // 使用 p1.level().isClientSide 判定
        if (p1.level().isClientSide) {
            try {
                var manager = FTBTeamsAPI.api().getClientManager();
                Optional<KnownClientPlayer> k1 = manager.getKnownPlayer(p1.getUUID());
                Optional<KnownClientPlayer> k2 = manager.getKnownPlayer(p2.getUUID());

                if (k1.isPresent() && k2.isPresent()) {
                    // 客户端 KnownClientPlayer 使用的是 teamId() 方法
                    return k1.get().teamId().equals(k2.get().teamId());
                }
            } catch (Exception e) {
                return false;
            }
        }

        return false;
    }
}
package net.lumalyte.lg.api;

import java.util.UUID;
import java.util.List;
import java.util.Set;

public interface GuildLookup {
    Set<UUID> getPlayerGuildIds(UUID player);
    GuildSummary getGuild(UUID guildId);
    List<GuildSummary> getAllGuilds();
    boolean isMember(UUID player, UUID guildId);
    boolean hasShopPermission(UUID player, UUID guildId, String permission);
    boolean hasRankAtLeast(UUID player, UUID guildId, String node);
    long getBankBalance(UUID guildId);
    boolean bankWithdraw(UUID guildId, UUID actor, long amount, String reason);
    boolean bankDeposit(UUID guildId, UUID actor, long amount, String reason);
}

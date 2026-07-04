package net.lumalyte.lg.domain.events;

import net.lumalyte.lg.api.GuildSummary;

public class GuildDisbandedEvent {
    private final GuildSummary guild;

    public GuildDisbandedEvent(GuildSummary guild) {
        this.guild = guild;
    }

    public GuildSummary getGuild() {
        return guild;
    }
}

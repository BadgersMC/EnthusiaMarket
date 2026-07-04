package net.lumalyte.lg.api;

import java.util.UUID;

public class GuildSummary {
    private final UUID id;
    private final String name;
    private final String tag;
    private final String emoji;

    public GuildSummary(UUID id, String name, String tag, String emoji) {
        this.id = id;
        this.name = name;
        this.tag = tag;
        this.emoji = emoji;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTag() {
        return tag;
    }

    public String getEmoji() {
        return emoji;
    }
}

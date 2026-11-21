package net.lumalyte.lg.api

import java.util.UUID

// Test-side contract for a server whose installed Guilds JAR exposes the optional API.
fun interface GuildMenuTitleLookup {
    fun title(guildId: UUID, viewerId: UUID, rows: Int, plainTitle: String): String?
}

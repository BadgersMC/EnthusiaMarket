package net.badgersmc.em.infrastructure.lumaguilds

import org.bukkit.Bukkit
import java.util.UUID

/** Uses only the registered public service and works with older Guilds JARs. */
internal object OptionalGuildMenuTitleLookup {
    fun title(guildId: String, viewer: UUID, rows: Int, plain: String): String = runCatching {
        val guild = UUID.fromString(guildId)
        val manager = Bukkit.getServicesManager()
        val type = manager.knownServices.firstOrNull { it.name == "net.lumalyte.lg.api.GuildMenuTitleLookup" }
            ?: return plain
        val provider = manager.load(type) ?: return plain
        val method = type.getMethod("title", UUID::class.java, UUID::class.java, Int::class.javaPrimitiveType, String::class.java)
        (method.invoke(provider, guild, viewer, rows, plain) as? String)?.takeIf { it.isNotBlank() } ?: plain
    }.getOrDefault(plain)
}

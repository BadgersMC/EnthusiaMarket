package net.badgersmc.em.infrastructure.lumaguilds

import org.bukkit.Bukkit
import java.util.UUID

/** Public Bukkit service only; older companions fail closed without a binary API requirement. */
internal object OptionalGuildAllianceLookup {
    fun areAllied(guild: UUID, ally: UUID): Boolean = runCatching {
        val manager = Bukkit.getServicesManager()
        val type = manager.knownServices.firstOrNull { it.name == "net.lumalyte.lg.api.GuildAllianceLookup" }
            ?: return false
        val provider = manager.load(type) ?: return false
        type.getMethod("areAllied", UUID::class.java, UUID::class.java).invoke(provider, guild, ally) == true
    }.getOrDefault(false)
}

/** REQ-399: LumaGuilds' prestige stall bonus; older LumaGuilds builds (no API) give 0. */
internal object OptionalGuildPrestigePerkLookup {
    fun extraGuildStalls(guild: UUID): Int = runCatching {
        val manager = Bukkit.getServicesManager()
        val type = manager.knownServices.firstOrNull { it.name == "net.lumalyte.lg.api.GuildPrestigePerkLookup" }
            ?: return 0
        val provider = manager.load(type) ?: return 0
        (type.getMethod("extraGuildStalls", UUID::class.java).invoke(provider, guild) as? Int) ?: 0
    }.getOrDefault(0)
}

package net.badgersmc.em.infrastructure.lumaguilds

import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.ServicePriority
import java.lang.reflect.Proxy
import java.util.UUID

/**
 * REQ-400: registers LumaGuilds' `GuildShopLookup` without compiling against it, so an older
 * LumaGuilds (no API) just shows no guild shop line. A failed lookup answers "no".
 */
internal object GuildShopLookupRegistration {
    const val API = "net.lumalyte.lg.api.GuildShopLookup"

    /** The API type from the loaded LumaGuilds, or null when it is missing or too old. */
    fun lumaGuildsApi(): Class<*>? = runCatching {
        val luma = Bukkit.getPluginManager().getPlugin("LumaGuilds") ?: return null
        Class.forName(API, false, luma.javaClass.classLoader)
    }.getOrNull()

    /** Registers [ownsShop] as [type]; false when [type] is null (an older LumaGuilds). */
    fun register(plugin: Plugin, type: Class<*>?, ownsShop: (UUID) -> Boolean): Boolean {
        if (type == null) return false
        val provider = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, args ->
            when (method.name) {
                "ownsShop" -> runCatching { ownsShop(args!![0] as UUID) }.getOrDefault(false)
                "equals" -> proxy === args?.get(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "EnthusiaMarket GuildShopLookup"
                else -> throw UnsupportedOperationException(method.name)
            }
        }
        @Suppress("UNCHECKED_CAST")
        Bukkit.getServicesManager().register(type as Class<Any>, provider, plugin, ServicePriority.Normal)
        return true
    }
}

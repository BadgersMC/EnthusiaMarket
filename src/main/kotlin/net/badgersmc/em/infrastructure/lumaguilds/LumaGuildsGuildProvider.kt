package net.badgersmc.em.infrastructure.lumaguilds

import net.badgersmc.em.domain.ports.GuildProvider
import net.badgersmc.nexus.annotations.Component
import net.lumalyte.lg.api.GuildLookup
import net.lumalyte.lg.api.GuildVisualLookup
import org.bukkit.Bukkit
import java.util.UUID

/**
 * Real [GuildProvider] backed by LumaGuilds' [GuildLookup] API.
 *
 * LumaGuilds registers [GuildLookup] in Bukkit's ServicesManager at enable, so we
 * load it via [org.bukkit.plugin.ServicesManager] rather than reaching into
 * LumaGuilds' Koin container — a cross-classloader Koin `get<T>()` LinkageErrors
 * on `kotlin.reflect.KClass`. The lookup is resolved lazily (LumaGuilds loads
 * BEFORE EnthusiaMarket, so it is registered by the time any command runs) and is
 * null only if LumaGuilds is absent; every method degrades to a safe default then.
 */
@Component
class LumaGuildsGuildProvider : GuildProvider {

    private val logger = java.util.logging.Logger.getLogger(LumaGuildsGuildProvider::class.java.name)

    private val lookup: GuildLookup? by lazy {
        Bukkit.getServicesManager().load(GuildLookup::class.java)
    }

    private val visualLookup: GuildVisualLookup? by lazy {
        Bukkit.getServicesManager().load(GuildVisualLookup::class.java)
    }
    private val dissolveHandlers = mutableListOf<(String) -> Unit>()

    override fun areAllied(guildId: String, otherGuildId: String): Boolean {
        val guild = parseUuid(guildId) ?: return false
        val ally = parseUuid(otherGuildId) ?: return false
        return OptionalGuildAllianceLookup.areAllied(guild, ally)
    }

    override fun extraGuildStalls(guildId: String): Int =
        parseUuid(guildId)?.let(OptionalGuildPrestigePerkLookup::extraGuildStalls) ?: 0

    override fun guildOf(player: UUID): GuildProvider.GuildRef? {
        val lg = lookup ?: return null
        val firstId = lg.getPlayerGuildIds(player).firstOrNull() ?: return null
        val guild = lg.getGuild(firstId) ?: return null
        return toGuildRef(guild)
    }

    override fun guildById(id: String): GuildProvider.GuildRef? {
        val uuid = parseUuid(id) ?: return null
        val guild = lookup?.getGuild(uuid) ?: return null
        return toGuildRef(guild)
    }

    override fun visualById(id: String): GuildProvider.GuildVisual? {
        val uuid = parseUuid(id) ?: return null
        val visual = visualLookup?.getGuildVisual(uuid) ?: return null
        return GuildProvider.GuildVisual(
            leaderId = visual.leaderId,
            banner = visual.banner?.let { banner ->
                GuildProvider.BannerDesign(
                    baseColor = banner.baseColor,
                    patterns = banner.patterns.map { GuildProvider.BannerPattern(it.type, it.color) },
                )
            },
        )
    }

    override fun listGuilds(): List<GuildProvider.GuildRef> =
        lookup?.getAllGuilds()?.map { toGuildRef(it) } ?: emptyList()

    override fun memberIds(guildId: String): Set<UUID> {
        val uuid = parseUuid(guildId) ?: return emptySet()
        // Primary path: query ALL guild members via the new GuildLookup API.
        // This fixes the "members cannot build after server restart" gap where
        // only online players were synced to WG, leaving offline members locked out.
        // Guarded against NoSuchMethodError — if the deployed LumaGuilds jar
        // predates getGuildMemberIds, fall through to the online-only path.
        val allMembers = try {
            lookup?.getGuildMemberIds(uuid)
        } catch (_: NoSuchMethodError) {
            null
        }
        if (allMembers != null) return allMembers

        // Fallback: if the new API is unavailable or returns empty, revert to
        // online-only filtering so existing behavior is preserved.
        return org.bukkit.Bukkit.getOnlinePlayers()
            .filter { lookup?.isMember(it.uniqueId, uuid) == true }
            .map { it.uniqueId }
            .toSet()
    }

    private fun parseUuid(value: String): UUID? =
        try {
            UUID.fromString(value)
        } catch (_: IllegalArgumentException) {
            null
        }

    override fun verifiedMembership(player: UUID, guildId: String): Boolean? {
        val uuid = parseUuid(guildId) ?: return null
        val source = lookup ?: return null
        if (source.getGuild(uuid) == null) return null
        return source.isMember(player, uuid)
    }

    override fun isMember(player: UUID, guildId: String): Boolean {
        val uuid = parseUuid(guildId) ?: return false
        return lookup?.isMember(player, uuid) ?: false
    }

    override fun hasShopPermission(player: UUID, guildId: String, permission: GuildProvider.GuildPermission): Boolean {
        val guildUuid = parseUuid(guildId) ?: return false
        val lgPermission = permission.toRankPermission() ?: return false
        return try {
            lookup?.hasShopPermission(player, guildUuid, lgPermission) ?: false
        } catch (e: Exception) {
            // Fail closed, but leave a trace — a thrown check here is unexpected
            // (the GuildLookup impl already fails closed internally).
            logger.log(java.util.logging.Level.WARNING, "hasShopPermission failed for player=$player guild=$guildId; denying", e)
            false
        }
    }

    @Deprecated("Use hasShopPermission with GuildPermission", replaceWith = ReplaceWith("hasShopPermission(player, guildId, permission)"))
    override fun hasPermission(player: UUID, guildId: String, node: String): Boolean {
        val guildUuid = parseUuid(guildId) ?: return false
        return lookup?.hasRankAtLeast(player, guildUuid, node) ?: false
    }

    override fun canWithdrawForStall(player: UUID, guildId: String): Boolean {
        val uuid = parseUuid(guildId) ?: return false
        return lookup?.hasShopPermission(player, uuid, "WITHDRAW_FROM_BANK") == true
    }

    override fun bankBalance(guildId: String): Long {
        val uuid = parseUuid(guildId) ?: return 0L
        return lookup?.getBankBalance(uuid) ?: 0L
    }

    override fun bankWithdraw(guildId: String, amount: Long): Boolean {
        if (amount !in 1..Int.MAX_VALUE.toLong()) return false
        val uuid = parseUuid(guildId) ?: return false
        return lookup?.systemBankWithdraw(uuid, amount, "EnthusiaMarket") ?: false
    }

    override fun bankDeposit(guildId: String, amount: Long): Boolean {
        if (amount !in 1..Int.MAX_VALUE.toLong()) return false
        val uuid = parseUuid(guildId) ?: return false
        return lookup?.systemBankDeposit(uuid, amount, "EnthusiaMarket") ?: false
    }

    override fun shopBankWithdraw(guildId: String, amount: Long, playerName: String, reversal: Boolean): Boolean {
        if (amount !in 1..Int.MAX_VALUE.toLong()) return false
        val uuid = parseUuid(guildId) ?: return false
        val role = if (reversal) "Buyer" else "Seller"
        return lookup?.systemBankWithdraw(uuid, amount, shopPaymentReason(role, playerName, reversal)) ?: false
    }

    override fun shopBankDeposit(guildId: String, amount: Long, playerName: String, reversal: Boolean): Boolean {
        if (amount !in 1..Int.MAX_VALUE.toLong()) return false
        val uuid = parseUuid(guildId) ?: return false
        val role = if (reversal) "Seller" else "Buyer"
        return lookup?.systemBankDeposit(uuid, amount, shopPaymentReason(role, playerName, reversal)) ?: false
    }

    private fun shopPaymentReason(role: String, playerName: String, reversal: Boolean): String {
        // Bound and sanitize player-controlled text before persisting it in the shared ledger.
        val safeName = playerName.takeIf { it.length in 1..32 && it.all(::isSafeShopNameCharacter) }
        val operation = if (reversal) "Shop payment reversal" else "Shop payment"
        return if (safeName == null) "$operation | EnthusiaMarket" else "$operation | $role: $safeName | EnthusiaMarket"
    }

    private fun isSafeShopNameCharacter(character: Char): Boolean =
        character.isLetterOrDigit() || character == '_' || character == '.' || character == ' '

    override fun onDissolved(handler: (String) -> Unit) {
        dissolveHandlers.add(handler)
    }

    /**
     * Called by [GuildDisbandedEventListener] when a [net.lumalyte.lg.domain.events.GuildDisbandedEvent]
     * is fired. Notifies all registered dissolve handlers.
     */
    internal fun handleDisbanded(guildId: String) {
        dissolveHandlers.toList().forEach { handler ->
            try {
                handler(guildId)
            } catch (e: Exception) {
                // Isolate handler failures — one failing handler shouldn't prevent others
            }
        }
    }

}

/**
 * Maps EM's [GuildProvider.GuildPermission] to LumaGuilds public API permission names.
 * Returns null if no direct mapping exists.
 */
private fun GuildProvider.GuildPermission.toRankPermission(): String? = when (this) {
    GuildProvider.GuildPermission.MANAGE_SHOPS -> "EDIT_SHOP_STOCK"
    GuildProvider.GuildPermission.ACCESS_SHOP_CHESTS -> "ACCESS_SHOP_CHESTS"
    GuildProvider.GuildPermission.EDIT_SHOP_STOCK -> "EDIT_SHOP_STOCK"
    GuildProvider.GuildPermission.MODIFY_SHOP_PRICES -> "MODIFY_SHOP_PRICES"
}

package net.badgersmc.em.interaction

import net.badgersmc.em.application.StallBuyoutService
import net.badgersmc.em.domain.ports.GuildProvider
import net.badgersmc.em.domain.stall.OwnerType
import net.badgersmc.em.domain.stall.StallId
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

/** Shared application adapter used by both Java and Bedrock stall-purchase UIs. */
object PurchaseFlow {
    data class GuildOption(val id: String, val name: String)

    fun eligibleGuild(player: Player, guildProvider: GuildProvider?): GuildOption? {
        val guild = guildProvider?.guildOf(player.uniqueId) ?: return null
        if (!guildProvider.hasShopPermission(
                player.uniqueId, guild.id, GuildProvider.GuildPermission.MANAGE_SHOPS,
            ) || !guildProvider.canWithdrawForStall(player.uniqueId, guild.id)) return null
        return GuildOption(guild.id, guild.name)
    }

    data class Offer(val stallId: StallId, val price: Long)

    fun execute(player: Player, offer: Offer, guild: GuildOption?, buyout: StallBuyoutService) =
        if (guild != null) buyout.buyForGuild(StallBuyoutService.GuildQuote(offer.stallId, offer.price, guild.id), player.uniqueId, playerIp(player))
        else buyout.buy(offer.stallId, player.uniqueId, offer.price, playerIp(player))

    fun message(result: StallBuyoutService.Result, stallId: StallId, lang: LangService): Component = when (result) {
        is StallBuyoutService.Result.Purchased -> lang.msg(
            if (result.owner.type == OwnerType.GUILD) "purchase_sign.msg.purchased_guild" else "purchase_sign.msg.purchased",
            "stall" to stallId.value, "price" to result.price,
        )
        is StallBuyoutService.Result.Rejected -> lang.msg("purchase_sign.msg.rejected", "reason" to result.reason)
        is StallBuyoutService.Result.NotInGuild -> lang.msg("purchase_sign.msg.not_in_guild")
        is StallBuyoutService.Result.NoGuildPermission -> lang.msg("purchase_sign.msg.no_guild_permission")
        else -> lang.msg(rejectionKey(result), "stall" to stallId.value)
    }

    private fun rejectionKey(result: StallBuyoutService.Result): String = when (result) {
        is StallBuyoutService.Result.NotFound -> "purchase_sign.msg.stall_missing"
        is StallBuyoutService.Result.AuctionLive -> "purchase_sign.msg.auction_live"
        is StallBuyoutService.Result.AlreadyOwned -> "purchase_sign.msg.already_owned"
        else -> error("Expected a purchase rejection")
    }

    private fun playerIp(player: Player): String = player.address?.address?.hostAddress ?: "unknown"
}

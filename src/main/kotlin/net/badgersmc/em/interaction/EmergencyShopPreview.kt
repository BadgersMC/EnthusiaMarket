package net.badgersmc.em.interaction

import net.badgersmc.em.domain.auction.Auction
import net.badgersmc.em.domain.auction.AuctionRepository
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.command.CommandSender

internal object EmergencyShopPreview {
    private const val PAGE_SIZE = 5
    private val commandWord = Regex("[A-Za-z0-9_.-]+")

    fun send(sender: CommandSender, auction: Auction, page: Int, auctions: AuctionRepository, lang: LangService) {
        if (page < 1) {
            sender.sendMessage(lang.msg("emergency_preview.invalid_page"))
            return
        }
        val rows = auctions.previewListings(auction.id, (page.toLong() - 1) * PAGE_SIZE, PAGE_SIZE + 1)
        sender.sendMessage(lang.msg("emergency_preview.heading", "stall" to auction.stallId.value, "page" to page))
        if (rows.isEmpty()) {
            sender.sendMessage(lang.msg(if (page == 1) "emergency_preview.empty" else "emergency_preview.invalid_page"))
        } else {
            sender.sendMessage(lang.msg("emergency_preview.notice"))
            for (row in rows.take(PAGE_SIZE)) {
                sender.sendMessage(lang.msg("emergency_preview.${row.direction.name.lowercase()}",
                    "quantity" to row.quantity, "item" to row.item, "amount" to row.amount, "payment" to row.payment))
            }
        }
        if (commandWord.matches(auction.stallId.value)) {
            val command = "/em preview ${auction.stallId.value} "
            if (page > 1) sender.sendMessage(lang.msg("emergency_preview.previous").clickEvent(ClickEvent.runCommand(command + (page - 1))))
            if (rows.size > PAGE_SIZE) sender.sendMessage(lang.msg("emergency_preview.next").clickEvent(ClickEvent.runCommand(command + (page + 1))))
        }
    }
}

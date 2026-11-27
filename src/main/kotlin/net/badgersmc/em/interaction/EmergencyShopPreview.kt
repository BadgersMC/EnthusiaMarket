package net.badgersmc.em.interaction

import net.badgersmc.em.domain.auction.Auction
import net.badgersmc.em.domain.auction.AuctionRepository
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.command.CommandSender

internal object EmergencyShopPreview {
    data class Request(
        val sender: CommandSender,
        val auction: Auction,
        val page: Int,
        val auctions: AuctionRepository,
        val lang: LangService,
    )

    private const val PAGE_SIZE = 5
    private val commandWord = Regex("[A-Za-z0-9_.-]+")

    fun send(request: Request) {
        if (request.page < 1) {
            request.sender.sendMessage(request.lang.msg("emergency_preview.invalid_page"))
            return
        }
        val rows = request.auctions.previewListings(
            request.auction.id, (request.page.toLong() - 1) * PAGE_SIZE, PAGE_SIZE + 1,
        )
        sendHeading(request)
        sendRows(request, rows)
        sendNavigation(request, rows.size)
    }

    private fun sendHeading(request: Request) {
        request.sender.sendMessage(request.lang.msg(
            "emergency_preview.heading", "stall" to request.auction.stallId.value, "page" to request.page,
        ))
    }

    private fun sendRows(request: Request, rows: List<net.badgersmc.em.domain.auction.EmergencyListing>) {
        if (rows.isEmpty()) {
            val key = if (request.page == 1) "emergency_preview.empty" else "emergency_preview.invalid_page"
            request.sender.sendMessage(request.lang.msg(key))
            return
        }
        request.sender.sendMessage(request.lang.msg("emergency_preview.notice"))
        rows.take(PAGE_SIZE).forEach { row ->
            request.sender.sendMessage(request.lang.msg(
                "emergency_preview.${row.direction.name.lowercase()}", "quantity" to row.quantity,
                "item" to row.item, "amount" to row.amount, "payment" to row.payment,
            ))
        }
    }

    private fun sendNavigation(request: Request, rowCount: Int) {
        val stall = request.auction.stallId.value
        if (!commandWord.matches(stall)) return
        val command = "/em preview $stall "
        if (request.page > 1) request.sender.sendMessage(
            request.lang.msg("emergency_preview.previous").clickEvent(ClickEvent.runCommand(command + (request.page - 1))),
        )
        if (rowCount > PAGE_SIZE) request.sender.sendMessage(
            request.lang.msg("emergency_preview.next").clickEvent(ClickEvent.runCommand(command + (request.page + 1))),
        )
    }
}

package net.badgersmc.em.infrastructure.commands

import net.badgersmc.em.domain.shop.ShopTransactionRepository
import net.badgersmc.nexus.commands.annotations.Arg
import net.badgersmc.nexus.commands.annotations.Context
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.paper.commands.annotations.Permission
import net.badgersmc.nexus.paper.commands.annotations.Subcommand
import org.bukkit.command.CommandSender

abstract class ShopHistoryRoutes(protected val transactions: ShopTransactionRepository, private val lang: LangService) {
    private val historyMessages: ShopHistoryMessages
        get() {
            return ShopHistoryMessages(lang, historyZone, transactions)
        }

    internal var historyClock: java.time.Clock = java.time.Clock.systemUTC()
    internal var historyZone: java.time.ZoneId = java.time.ZoneId.systemDefault()

    @Subcommand("history")
    @Permission("enthusiamarket.shop.use")
    fun history(@Context sender: CommandSender) = history(sender, 1)

    @Subcommand("history")
    @Permission("enthusiamarket.shop.use")
    fun history(@Context sender: CommandSender, @Arg("page") page: Int) {
        historyMessages.read(sender, page, ShopHistorySelection(null, "/shop history", "all"))
    }

    @Subcommand("history all")
    @Permission("enthusiamarket.shop.use")
    fun historyAll(@Context sender: CommandSender) = historyAll(sender, 1)

    @Subcommand("history all")
    @Permission("enthusiamarket.shop.use")
    fun historyAll(@Context sender: CommandSender, @Arg("page") page: Int) {
        historyMessages.read(sender, page, ShopHistorySelection(null, "/shop history all", "all"))
    }

    @Subcommand("history today")
    @Permission("enthusiamarket.shop.use")
    fun historyToday(@Context sender: CommandSender) = historyToday(sender, 1)

    @Subcommand("history today")
    @Permission("enthusiamarket.shop.use")
    fun historyToday(@Context sender: CommandSender, @Arg("page") page: Int) {
        val window = net.badgersmc.em.application.ShopHistoryDates.today(historyClock, historyZone)
        historyMessages.read(sender, page, ShopHistorySelection(window, "/shop history today", "today"))
    }

    @Subcommand("history range")
    @Permission("enthusiamarket.shop.use")
    fun historyRange(
        @Context sender: CommandSender,
        @Arg("from") from: String,
        @Arg("to") to: String,
    ) = historyRange(sender, from, to, 1)

    @Subcommand("history range")
    @Permission("enthusiamarket.shop.use")
    fun historyRange(
        @Context sender: CommandSender,
        @Arg("from") from: String,
        @Arg("to") to: String,
        @Arg("page") page: Int,
    ) {
        val window = try {
            net.badgersmc.em.application.ShopHistoryDates.range(from, to, historyZone)
        } catch (_: java.time.DateTimeException) {
            sender.sendMessage(lang.msg("shop.history.invalid_dates")); return
        } catch (_: IllegalArgumentException) {
            sender.sendMessage(lang.msg("shop.history.invalid_dates")); return
        }
        historyMessages.read(sender, page, ShopHistorySelection(window, "/shop history range $from $to", "$from to $to"))
    }
}

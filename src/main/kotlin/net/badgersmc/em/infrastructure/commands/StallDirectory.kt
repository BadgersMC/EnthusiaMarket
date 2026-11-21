package net.badgersmc.em.infrastructure.commands

import net.badgersmc.em.domain.stall.Stall
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.command.CommandSender
import net.badgersmc.em.infrastructure.commands.AdminCommands.Companion.LIST_PAGE_SIZE

internal class StallDirectory(private val lang: LangService) {
    fun send(sender: CommandSender, stalls: Collection<Stall>, page: Int) {
        val ordered = stalls.sortedWith(compareBy({ it.world }, { it.id.value.trimEnd { c -> c in '0'..'9' } },
            { numericSuffix(it.id.value).length }, { numericSuffix(it.id.value) }, { it.id.value }))
        if (ordered.isEmpty()) {
            sender.sendMessage(lang.msg("admin.list.empty"))
            return
        }
        val pages = (ordered.size - 1) / LIST_PAGE_SIZE + 1
        if (page !in 1..pages) {
            sender.sendMessage(lang.msg("admin.list.invalid", "pages" to pages))
            return
        }
        sender.sendMessage(lang.msg("admin.list.header", "count" to ordered.size, "page" to page, "pages" to pages))
        for (stall in ordered.drop((page - 1) * LIST_PAGE_SIZE).take(LIST_PAGE_SIZE)) {
            val id = stall.id.value
            val match = NUMBERED_STALL.matchEntire(id)
            val label = if (match == null) id else {
                val number = match.groupValues[2].trimStart('0').ifEmpty { "0" }
                if (match.groupValues[1].isEmpty()) "Stall $number" else "Market stall $number"
            }
            val status = lang.msg("admin.list.states." + stall.state.name.lowercase())
            val row = lang.msg("admin.list.row", "label" to label).append(status)
                .hoverEvent(lang.msg("admin.list.details", "id" to id, "world" to stall.world, "region" to stall.regionId))
            sender.sendMessage(if (id.matches(SAFE_STALL_ARGUMENT)) row.clickEvent(ClickEvent.runCommand("/em stall info $id")) else row)
        }
        val nav = Component.text()
        if (page > 1) nav.append(lang.msg("admin.list.previous").clickEvent(ClickEvent.runCommand("/em list page ${page - 1}")))
        if (page in 2 until pages) nav.append(Component.space())
        if (page < pages) nav.append(lang.msg("admin.list.next").clickEvent(ClickEvent.runCommand("/em list page ${page + 1}")))
        if (pages > 1) sender.sendMessage(nav.build())
    }
}

private fun numericSuffix(id: String) = id.takeLastWhile { it in '0'..'9' }.trimStart('0')
private val NUMBERED_STALL = Regex("stall(_cm_)?([0-9]+)", RegexOption.IGNORE_CASE)
private val SAFE_STALL_ARGUMENT = Regex("[A-Za-z0-9_.-]+")

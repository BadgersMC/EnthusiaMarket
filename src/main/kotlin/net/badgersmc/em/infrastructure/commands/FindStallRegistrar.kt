package net.badgersmc.em.infrastructure.commands

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import net.badgersmc.em.application.StallAccessIndex
import net.badgersmc.em.domain.stall.Stall
import net.badgersmc.em.domain.stall.StallState
import net.badgersmc.em.infrastructure.listeners.FinderTrailService
import net.badgersmc.nexus.core.NexusContext
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

internal object FindStallRegistrar {
    private const val PAGE_SIZE = 6

    fun register(plugin: Plugin, nexus: NexusContext) {
        plugin.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val index = nexus.getBean(StallAccessIndex::class)
            val trail = nexus.getBean(FinderTrailService::class)
            val lang = nexus.getBean(LangService::class)
            val root = Commands.literal("findstall")
                .requires { it.sender is Player && it.sender.hasPermission("enthusiamarket.shop.use") }
                .executes { browse(it.source.sender as Player, index, lang, 1) }
            root.then(Commands.literal("page").then(Commands.argument("number", IntegerArgumentType.integer(1))
                .executes { browse(it.source.sender as Player, index, lang, IntegerArgumentType.getInteger(it, "number")) }))
            root.then(Commands.argument("stall", StringArgumentType.string())
                .suggests { _, builder ->
                    val prefix = builder.remaining.removePrefix("\"")
                    publicStalls(index).map { it.id.value }.filter { it.startsWith(prefix, ignoreCase = true) }
                        .forEach { builder.suggest(argument(it)) }
                    builder.buildFuture()
                }
                .executes {
                    val player = it.source.sender as Player
                    val id = StringArgumentType.getString(it, "stall")
                    val stall = index.cached(id)?.takeIf { s -> index.isFresh(id) && visible(s) }
                    if (stall == null) {
                        player.sendMessage(lang.msg("stall_finder.invalid"))
                        0
                    } else {
                        trail.startStall(player, stall)
                        1
                    }
                })
            event.registrar().register(root.build(), "Browse market stalls and follow a private guide")
        }
    }

    private fun visible(stall: Stall) = stall.state != StallState.MODERATION_HOLD
    private fun publicStalls(index: StallAccessIndex) = index.currentStalls().filter(::visible)
    private fun argument(id: String) = if (id == "page") "\"page\"" else StringArgumentType.escapeIfRequired(id)

    internal fun browse(player: Player, index: StallAccessIndex, lang: LangService, page: Int): Int {
        val stalls = publicStalls(index).sortedBy { it.id.value }
        if (stalls.isEmpty()) { player.sendMessage(lang.msg("stall_finder.empty")); return 0 }
        val pages = (stalls.size - 1) / PAGE_SIZE + 1
        if (page !in 1..pages) { player.sendMessage(lang.msg("stall_finder.page", "pages" to pages)); return 0 }
        player.sendMessage(lang.msg("stall_finder.header", "page" to page, "pages" to pages))
        stalls.drop((page - 1) * PAGE_SIZE).take(PAGE_SIZE).forEach { stall ->
            player.sendMessage(lang.msg("stall_finder.row", "stall" to stall.id.value)
                .clickEvent(ClickEvent.runCommand("/findstall " + argument(stall.id.value))))
        }
        val controls = listOfNotNull(
            if (page > 1) lang.msg("stall_finder.previous").clickEvent(ClickEvent.runCommand("/findstall page " + (page - 1))) else null,
            if (page < pages) lang.msg("stall_finder.next").clickEvent(ClickEvent.runCommand("/findstall page " + (page + 1))) else null,
        )
        if (controls.isNotEmpty()) player.sendMessage(Component.join(net.kyori.adventure.text.JoinConfiguration.separator(Component.space()), controls))
        return 1
    }
}

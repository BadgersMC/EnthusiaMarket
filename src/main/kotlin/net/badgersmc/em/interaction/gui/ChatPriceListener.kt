package net.badgersmc.em.interaction.gui

import net.badgersmc.nexus.paper.listeners.Listener
import org.bukkit.event.EventHandler
import org.bukkit.event.player.AsyncPlayerChatEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Listener that parses a chat message as a custom price/amount for [CreateShopMenu].
 * Registered via Nexus DI with the [Listener] annotation.
 */
@Listener
class ChatPriceListener : org.bukkit.event.Listener {
    companion object {
        val waiting = ConcurrentHashMap<UUID, CreateShopMenu>()
    }

    @EventHandler
    fun onChat(event: AsyncPlayerChatEvent) {
        val menu = waiting.remove(event.player.uniqueId) ?: return
        event.isCancelled = true
        val input = event.message.trim()
        if (input.equals("cancel", ignoreCase = true)) {
            event.player.sendMessage(menu.internalLang.msg("gui.shop.create.custom_price_cancelled"))
            org.bukkit.Bukkit.getScheduler().runTask(
                org.bukkit.Bukkit.getPluginManager().getPlugin("EnthusiaMarket")!!,
                Runnable { menu.open(event.player) }
            )
            return
        }
        val parsed = input.toLongOrNull()
        if (parsed == null || parsed < 1) {
            event.player.sendMessage(menu.internalLang.msg("gui.shop.create.custom_price_invalid"))
            waiting[event.player.uniqueId] = menu
            return
        }
        menu.setPrice(parsed)
        org.bukkit.Bukkit.getScheduler().runTask(
            org.bukkit.Bukkit.getPluginManager().getPlugin("EnthusiaMarket")!!,
            Runnable { menu.open(event.player) }
        )
    }
}

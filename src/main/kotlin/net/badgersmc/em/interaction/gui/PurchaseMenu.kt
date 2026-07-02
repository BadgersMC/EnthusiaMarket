package net.badgersmc.em.interaction.gui

import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder
import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.badgersmc.em.application.ContainerTradeResult
import net.badgersmc.em.application.ContainerTradeService
import net.badgersmc.em.application.ItemStackSerializer
import net.badgersmc.em.domain.shop.Shop
import net.badgersmc.em.domain.shop.SignDirection
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.em.interaction.Menu
import net.badgersmc.em.interaction.blockItemTheft
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta

/**
 * Trade GUI for a sign shop. Direction-aware (REQ-006):
 *
 * Uses "YOU RECEIVE" / "YOU GIVE" labels to remove BUY/SELL confusion.
 *
 * - **SELL** sign: YOU RECEIVE the shop's item, YOU GIVE currency.
 * - **BUY** sign:  YOU RECEIVE currency, YOU GIVE the shop's item.
 * - **TRADE** sign: YOU RECEIVE the shop's item, YOU GIVE a specific item.
 */
class PurchaseMenu(
    private val shop: Shop,
    private val tradeService: ContainerTradeService,
    private val lang: LangService,
) : Menu {

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    override fun open(player: Player) {
        val sellStack = ItemStackSerializer.deserialize(shop.sellItem)
        val sellName = sellStack?.type?.name?.lowercase()?.replace('_', ' ') ?: "?"
        val costStack = if (shop.direction == SignDirection.TRADE) {
            ItemStackSerializer.deserialize(shop.costItem)
        } else null
        val costName = costStack?.type?.name?.lowercase()?.replace('_', ' ') ?: "currency"

        val ownerName = Bukkit.getOfflinePlayer(shop.owner).name ?: "Unknown"
        dirLabel = ShopDisplay.directionLabel(shop.direction)

        // Chat direction reminder
        val chatHintKey = when (shop.direction) {
            SignDirection.SELL -> "gui.shop.chat_reminder_buy"
            SignDirection.BUY -> "gui.shop.chat_reminder_sell"
            SignDirection.TRADE -> "gui.shop.chat_reminder_trade"
        }
        player.sendMessage(lang.msg(chatHintKey, "owner" to ownerName, "item" to sellName, "amount" to shop.sellAmount))

        render(player, sellStack, costStack, sellName, costName, ownerName)
    }

    private var multiplier = 1
    private lateinit var dirLabel: String

    @Suppress("LongMethod")
    private fun render(
        player: Player,
        sellStack: ItemStack?,
        costStack: ItemStack?,
        sellName: String,
        costName: String,
        ownerName: String,
    ) {
        val gui = ChestGui(3, ComponentHolder.of(lang.msg("gui.shop.title", "amount" to (shop.sellAmount * multiplier))))
        val pane = StaticPane(9, 3)

        // --- Row 0: YOU RECEIVE / arrow / YOU GIVE labels ---
        val receiveLabel = lang.msg("gui.shop.receive_label")
        val giveLabel = lang.msg("gui.shop.give_label")
        pane.addItem(GuiItem(decorated(Material.GREEN_STAINED_GLASS_PANE, receiveLabel)), 2, 0)
        pane.addItem(GuiItem(decorated(Material.ARROW, Component.text("→"))), 4, 0)
        pane.addItem(GuiItem(decorated(Material.RED_STAINED_GLASS_PANE, giveLabel)), 6, 0)

        // --- Row 1: the actual items ---
        val receiveItem: ItemStack
        val receiveName: Component
        val receiveLore: List<Component>
        val giveItem: ItemStack
        val giveName: Component
        val giveLore: List<Component>

        when (shop.direction) {
            SignDirection.SELL -> {
                val totalAmount = shop.sellAmount * multiplier
                val totalCost = shop.costAmount * multiplier
                receiveItem = sellStack?.clone() ?: ItemStack(Material.BARRIER)
                receiveName = lang.msg("gui.shop.receive_sell", "amount" to totalAmount, "item" to sellName)
                receiveLore = listOf(
                    lang.msg("gui.shop.sell_lore_stock", "stock" to ShopDisplay.tradesAvailable(shop)),
                    lang.msg("gui.shop.sell_lore_owner", "owner" to ownerName),
                )
                giveItem = ItemStack(Material.EMERALD)
                giveName = lang.msg("gui.shop.give_currency", "cost" to totalCost)
                giveLore = listOf(lang.msg("gui.shop.give_currency_lore", "cost" to totalCost))
            }
            SignDirection.BUY -> {
                val totalAmount = shop.sellAmount * multiplier
                val totalCost = shop.costAmount * multiplier
                receiveItem = ItemStack(Material.EMERALD)
                receiveName = lang.msg("gui.shop.receive_currency", "cost" to totalCost)
                receiveLore = listOf(lang.msg("gui.shop.receive_currency_lore", "cost" to totalCost))
                giveItem = sellStack?.clone() ?: ItemStack(Material.BARRIER)
                giveName = lang.msg("gui.shop.give_item", "amount" to totalAmount, "item" to sellName)
                giveLore = listOf(
                    lang.msg("gui.shop.sell_lore_stock", "stock" to ShopDisplay.tradesAvailable(shop)),
                    lang.msg("gui.shop.sell_lore_owner", "owner" to ownerName),
                )
            }
            SignDirection.TRADE -> {
                val totalAmount = shop.sellAmount * multiplier
                val totalCost = shop.costAmount * multiplier
                receiveItem = sellStack?.clone() ?: ItemStack(Material.BARRIER)
                receiveName = lang.msg("gui.shop.receive_sell", "amount" to totalAmount, "item" to sellName)
                receiveLore = listOf(
                    lang.msg("gui.shop.sell_lore_stock", "stock" to ShopDisplay.tradesAvailable(shop)),
                )
                giveItem = costStack?.clone() ?: ItemStack(Material.BARRIER)
                giveName = lang.msg("gui.shop.give_trade_item", "amount" to totalCost, "item" to costName)
                giveLore = listOf(lang.msg("gui.shop.give_trade_item_lore", "amount" to totalCost))
            }
        }

        pane.addItem(GuiItem(decorated(receiveItem, receiveName, receiveLore)), 2, 1)
        pane.addItem(GuiItem(decorated(Material.ARROW, Component.text("→"))), 4, 1)
        pane.addItem(GuiItem(decorated(giveItem, giveName, giveLore)), 6, 1)

        // --- Row 2: multiplier controls + confirm ---
        // [-1] [xN] [+1] ... [Confirm]
        pane.addItem(GuiItem(decorated(Material.RED_DYE,
            lang.msg("gui.shop.create.btn_minus1", "delta" to -1, "val" to (multiplier - 1)))) { event ->
            event.isCancelled = true
            if (multiplier > 1) { multiplier--; render(player, sellStack, costStack, sellName, costName, ownerName) }
        }, 1, 2)

        pane.addItem(GuiItem(decorated(Material.PAPER,
            Component.text("x$multiplier", NamedTextColor.WHITE))), 2, 2)

        pane.addItem(GuiItem(decorated(Material.LIME_DYE,
            lang.msg("gui.shop.create.btn_plus1", "delta" to 1, "val" to (multiplier + 1)))) { event ->
            event.isCancelled = true
            val maxTrades = ShopDisplay.tradesAvailable(shop)
            if (multiplier < maxTrades.coerceAtMost(64)) { multiplier++; render(player, sellStack, costStack, sellName, costName, ownerName) }
        }, 3, 2)

        val buttonKey = when (shop.direction) {
            SignDirection.SELL -> "gui.shop.confirm_buy"
            SignDirection.BUY -> "gui.shop.confirm_sell"
            SignDirection.TRADE -> "gui.shop.confirm_trade"
        }
        val buttonLore = listOf(
            lang.msg("gui.shop.confirm_lore", "dir" to dirLabel, "direction" to dirLabel),
        )

        pane.addItem(GuiItem(decorated(
            Material.LIME_STAINED_GLASS_PANE, lang.msg(buttonKey), buttonLore,
        )) { event ->
            event.isCancelled = true
            var lastResult: net.badgersmc.em.application.ContainerTradeResult = net.badgersmc.em.application.ContainerTradeResult.Success("")
            for (i in 1..multiplier) {
                val result = when (shop.direction) {
                    SignDirection.SELL -> tradeService.executeSell(shop, player.uniqueId)
                    SignDirection.BUY -> tradeService.executeBuy(shop, player.uniqueId)
                    SignDirection.TRADE -> tradeService.executeTrade(shop, player.uniqueId)
                }
                lastResult = result
                if (result !is ContainerTradeResult.Success) break
            }
            when (lastResult) {
                is ContainerTradeResult.Success -> player.sendMessage(
                    lang.msg("shop.trade.success", "message" to lastResult.message),
                )
                is ContainerTradeResult.Failure -> player.sendMessage(
                    lang.msg("shop.trade.failure", "reason" to lastResult.reason),
                )
                is ContainerTradeResult.CompensationFailed -> {
                    player.sendMessage(lang.msg("shop.trade.compensation_failed", "error" to lastResult.error))
                    player.sendMessage(lang.msg("shop.trade.compensation_note", "compensation" to lastResult.compensation))
                }
            }
            multiplier = 1
            render(player, sellStack, costStack, sellName, costName, ownerName)
        }, 5, 2)

        // Shulker box preview button (IS2-12, REQ-298)
        addShulkerPreview(pane, player)

        gui.addPane(pane)
        gui.blockItemTheft()
        gui.show(player)
    }

    private fun decorated(material: Material, name: Component, lore: List<Component> = emptyList()): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        meta.displayName(name)
        if (lore.isNotEmpty()) meta.lore(lore)
        item.itemMeta = meta
        return item
    }

    private fun decorated(base: ItemStack, name: Component, lore: List<Component> = emptyList()): ItemStack {
        val item = base.clone()
        val meta = item.itemMeta ?: return item
        meta.displayName(name)
        if (lore.isNotEmpty()) meta.lore(lore)
        item.itemMeta = meta
        return item
    }

    /** IS2-12, REQ-298: add a shulker preview button when the shop sells a shulker box. */
    private fun addShulkerPreview(pane: StaticPane, player: Player) {
        val sellStack = ItemStackSerializer.deserialize(shop.sellItem) ?: return
        if ((sellStack.itemMeta as? BlockStateMeta)?.blockState !is org.bukkit.block.ShulkerBox) return
        pane.addItem(GuiItem(decorated(
            Material.SHULKER_BOX,
            lang.msg("gui.shop.shulker_preview_name"),
            listOf(lang.msg("gui.shop.shulker_preview_lore")),
        )) { event ->
            event.isCancelled = true
            ShulkerPreviewMenu(sellStack.clone(), lang).open(player)
        }, 8, 0)
    }
}

package net.badgersmc.em.infrastructure.commands

import net.badgersmc.em.application.ItemStackSerializer
import net.badgersmc.em.application.ShopManagementService
import net.badgersmc.nexus.commands.annotations.Command
import net.badgersmc.nexus.commands.annotations.Context
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.paper.commands.annotations.Permission
import net.badgersmc.nexus.paper.commands.annotations.Subcommand
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Player-facing `/shop` command (ItemShops parity sub-project 1). Menu-driven,
 * matching ItemShops: list / edit / trust / untrust / delete / breakdelete.
 */
@Command(name = "shop", description = "Manage your shops", aliases = ["shops"])
class ShopCommands(
    private val management: ShopManagementService,
    private val lang: LangService,
) {
    @Subcommand("list")
    @Permission("enthusiamarket.shop.use")
    fun list(@Context sender: CommandSender) {
        val player = sender as? Player ?: run { sender.sendMessage(lang.msg("shop.cmd.players_only")); return }
        val shops = management.shopsOwnedBy(player.uniqueId)
        if (shops.isEmpty()) {
            player.sendMessage(lang.msg("shop.cmd.none_owned"))
            return
        }
        player.sendMessage(lang.msg("shop.cmd.list_header", "count" to shops.size))
        for (s in shops) {
            val sellName = ItemStackSerializer.deserialize(s.sellItem)?.type?.name?.lowercase() ?: "?"
            player.sendMessage(
                lang.msg(
                    "shop.cmd.list_line",
                    "world" to s.signWorld, "x" to s.signX, "y" to s.signY, "z" to s.signZ,
                    "sell_amt" to s.sellAmount, "sell" to sellName, "cost" to s.costAmount,
                )
            )
        }
    }
}
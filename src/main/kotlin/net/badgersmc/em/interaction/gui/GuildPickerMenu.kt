package net.badgersmc.em.interaction.gui

import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder
import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import net.badgersmc.em.interaction.blockItemTheft
import com.github.stefvanschie.inventoryframework.pane.OutlinePane
import com.github.stefvanschie.inventoryframework.pane.Pane
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import com.github.stefvanschie.inventoryframework.pane.util.Slot
import net.badgersmc.em.application.GuildTradePolicyService
import net.badgersmc.em.domain.ports.GuildProvider
import net.badgersmc.em.interaction.Menu
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID

/** Picks a target guild for a new policy; seeds a default tariff then returns to the policy menu. */
class GuildPickerMenu(
    private val actor: UUID,
    private val ownerGuildId: String,
    private val policyService: GuildTradePolicyService,
    private val guildProvider: GuildProvider,
    private val lang: LangService,
) : Menu {

    override fun open(player: Player) = render(player)

    private fun render(player: Player, requestedPage: Int = 0) {
        val existing = policyService.list(ownerGuildId).map { it.targetGuildId }.toSet()
        val targets = selectable(guildProvider.listGuilds(), ownerGuildId, existing)
        val page = requestedPage.coerceIn(0, ((targets.size - 1) / PER_PAGE).coerceAtLeast(0))
        val visible = targets.drop(page * PER_PAGE).take(PER_PAGE)
        val rows = guildPolicyContentRows(visible.size)
        val gui = ChestGui(rows + 1, ComponentHolder.of(lang.msg("gui.guildpicker.title")))
        val pane = OutlinePane(9, rows, Pane.Priority.LOWEST)
        visible.forEach { ref ->
            val banner = guildPolicyBanner(
                guildProvider.visualById(ref.id)?.banner,
                lang.msg("gui.guildpicker.entry", "name" to ref.name),
                lang.msg("gui.guildpicker.select_tariff", "rate" to DEFAULT_NEW_TARIFF),
            )
            pane.addItem(GuiItem(banner) { ev ->
                ev.isCancelled = true
                if (ev.isLeftClick) pickTarget(player, ref)
            })
        }
        gui.addPane(Slot.fromXY(0, 0), pane)
        gui.addPane(Slot.fromXY(0, rows), controls(player, page, (page + 1) * PER_PAGE < targets.size))
        gui.blockItemTheft()
        gui.show(player)
    }

    private fun pickTarget(player: Player, ref: GuildProvider.GuildRef) {
        when (val result = policyService.setTariff(actor, ownerGuildId, ref.id, DEFAULT_NEW_TARIFF)) {
            is GuildTradePolicyService.PolicyResult.Invalid ->
                player.sendMessage(lang.msg("gui.guildpicker.invalid", "reason" to result.reason))
            GuildTradePolicyService.PolicyResult.Denied ->
                player.sendMessage(lang.msg("gui.guildpolicy.denied"))
            GuildTradePolicyService.PolicyResult.Ok ->
                GuildTradePolicyMenu(actor, ownerGuildId, policyService, guildProvider, lang).open(player)
        }
    }

    private fun controls(player: Player, page: Int, hasNext: Boolean): StaticPane {
        val bar = StaticPane(9, 1)
        bar.addItem(guildPolicyGuide(lang), 1, 0)
        if (page > 0) bar.addItem(GuiItem(named(Material.ARROW, lang.msg("gui.common.prev"))) {
            it.isCancelled = true
            render(player, page - 1)
        }, 0, 0)
        bar.addItem(GuiItem(named(Material.BARRIER, lang.msg("gui.common.back"))) {
            it.isCancelled = true; GuildTradePolicyMenu(actor, ownerGuildId, policyService, guildProvider, lang).open(player)
        }, 4, 0)
        if (hasNext) bar.addItem(GuiItem(named(Material.ARROW, lang.msg("gui.common.next"))) {
            it.isCancelled = true
            render(player, page + 1)
        }, 8, 0)
        return bar
    }

    private fun named(material: Material, name: Component): ItemStack =
        ItemStack(material).apply { itemMeta = itemMeta?.also { it.displayName(name) } }

    companion object {
        const val DEFAULT_NEW_TARIFF = 10
        private const val PER_PAGE = 45
        /** Guilds eligible as new policy targets: all guilds minus the owner and already-policied ids. */
        fun selectable(all: List<GuildProvider.GuildRef>, ownerGuildId: String, existingTargets: Set<String>): List<GuildProvider.GuildRef> =
            all.filter { it.id != ownerGuildId && it.id !in existingTargets }
    }
}

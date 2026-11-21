package net.badgersmc.em.interaction.gui

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import net.badgersmc.em.domain.ports.GuildProvider
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import org.bukkit.DyeColor
import org.bukkit.Material
import org.bukkit.block.banner.Pattern
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BannerMeta

internal fun guildPolicyBanner(design: GuildProvider.BannerDesign?, name: Component, vararg lore: Component): ItemStack {
    val base = DyeColor.entries.firstOrNull { it.name.equals(design?.baseColor, ignoreCase = true) } ?: DyeColor.WHITE
    val item = ItemStack(Material.valueOf("${base.name}_BANNER"))
    val meta = item.itemMeta as BannerMeta
    for (layer in design?.patterns.orEmpty()) {
        val color = DyeColor.entries.firstOrNull { it.name.equals(layer.color, ignoreCase = true) } ?: continue
        val key = NamespacedKey.fromString(layer.type.lowercase()) ?: continue
        val type = Registry.BANNER_PATTERN.get(key) ?: continue
        meta.addPattern(Pattern(color, type))
    }
    meta.displayName(name)
    meta.lore(lore.toList())
    item.itemMeta = meta
    return item
}

internal fun guildPolicyGuide(lang: LangService): GuiItem {
    val guide = ItemStack(Material.WRITTEN_BOOK)
    guide.editMeta { meta ->
        meta.displayName(lang.msg("gui.guildpolicy.guide_title"))
        meta.lore(listOf("scope", "tariff", "barter", "embargo", "clear").map { lang.msg("gui.guildpolicy.guide_$it") })
    }
    return GuiItem(guide) { it.isCancelled = true }
}

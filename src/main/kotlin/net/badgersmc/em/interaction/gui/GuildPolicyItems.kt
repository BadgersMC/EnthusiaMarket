package net.badgersmc.em.interaction.gui

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import net.badgersmc.em.domain.ports.GuildProvider
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import org.bukkit.DyeColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.block.banner.Pattern
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BannerMeta

internal fun guildPolicyContentRows(entries: Int): Int = (entries.coerceIn(1, 45) + 8) / 9

internal fun guildPolicyBanner(design: GuildProvider.BannerDesign?, name: Component, vararg lore: Component): ItemStack {
    val base = DyeColor.entries.firstOrNull { it.name.equals(design?.baseColor, ignoreCase = true) } ?: DyeColor.WHITE
    val item = ItemStack(Material.valueOf("${base.name}_BANNER"))
    val meta = item.itemMeta as BannerMeta
    meta.patterns = design?.patterns.orEmpty().mapNotNull(::bannerPattern)
    meta.displayName(name)
    meta.lore(lore.toList())
    item.itemMeta = meta
    return item
}

private fun bannerPattern(layer: GuildProvider.BannerPattern): Pattern? {
    val color = DyeColor.entries.firstOrNull { it.name.equals(layer.color, ignoreCase = true) } ?: return null
    val key = NamespacedKey.fromString(layer.type.lowercase()) ?: return null
    return Registry.BANNER_PATTERN.get(key)?.let { Pattern(color, it) }
}

internal fun guildPolicyGuide(lang: LangService): GuiItem {
    val guide = ItemStack(Material.WRITTEN_BOOK)
    guide.editMeta { meta ->
        meta.displayName(lang.msg("gui.guildpolicy.guide_title"))
        meta.lore(listOf("scope", "tariff", "barter", "embargo", "clear").map { lang.msg("gui.guildpolicy.guide_$it") })
    }
    return GuiItem(guide) { it.isCancelled = true }
}

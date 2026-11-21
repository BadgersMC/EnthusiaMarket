package net.badgersmc.em.interaction.gui

import net.badgersmc.em.domain.ports.GuildProvider
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.inventory.meta.BannerMeta
import org.mockbukkit.mockbukkit.MockBukkit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GuildPolicyBannerTest {
    @BeforeTest fun start() { MockBukkit.mock() }
    @AfterTest fun stop() { MockBukkit.unmock() }

    @Test fun `banner preserves base and ordered layers around an unknown layer`() {
        val design = GuildProvider.BannerDesign("BLUE", listOf(
            GuildProvider.BannerPattern("STRIPE_TOP", "WHITE"),
            GuildProvider.BannerPattern("UNKNOWN_PATTERN", "WHITE"),
            GuildProvider.BannerPattern("CROSS", "BLACK"),
        ))
        val item = guildPolicyBanner(design, Component.text("Guild"))
        assertEquals(Material.BLUE_BANNER, item.type)
        val patterns = (item.itemMeta as BannerMeta).patterns
        assertEquals(listOf("stripe_top", "cross"), patterns.map { it.pattern.key.key })
        assertEquals(listOf("WHITE", "BLACK"), patterns.map { it.color.name })
    }

    @Test fun `missing design still has a named banner`() {
        val name = Component.text("Guild")
        val item = guildPolicyBanner(null, name)
        assertEquals(Material.WHITE_BANNER, item.type)
        assertEquals(name, item.itemMeta.displayName())
    }
}

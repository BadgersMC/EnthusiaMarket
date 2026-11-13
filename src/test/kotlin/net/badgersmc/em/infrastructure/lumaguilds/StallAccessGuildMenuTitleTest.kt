package net.badgersmc.em.infrastructure.lumaguilds

import org.bukkit.Bukkit
import org.bukkit.plugin.ServicePriority
import org.mockbukkit.mockbukkit.MockBukkit
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import net.lumalyte.lg.api.GuildMenuTitleLookup

class StallAccessGuildMenuTitleTest {
    @BeforeTest fun start() { MockBukkit.mock() }
    @AfterTest fun stop() { MockBukkit.unmock() }

    @Test fun `older Guilds leaves the plain title intact`() {
        assertEquals("Visitor access", OptionalGuildMenuTitleLookup.title(UUID.randomUUID().toString(), UUID.randomUUID(), 6, "Visitor access"))
    }

    @Test fun `registered Guilds bridge receives the stall owner and current viewer`() {
        val guild = UUID.randomUUID()
        val player = UUID.randomUUID()
        val plugin = MockBukkit.createMockPlugin()
        Bukkit.getServicesManager().register(GuildMenuTitleLookup::class.java, GuildMenuTitleLookup { owner, viewer, rows, plain ->
            if (owner == guild && viewer == player && rows == 6) "<glyph:guild_bg_enthusia_6_row>$plain" else null
        }, plugin, ServicePriority.Normal)

        assertEquals("<glyph:guild_bg_enthusia_6_row>Visitor access", OptionalGuildMenuTitleLookup.title(guild.toString(), player, 6, "Visitor access"))
        assertEquals("Visitor access", OptionalGuildMenuTitleLookup.title(guild.toString(), UUID.randomUUID(), 6, "Visitor access"))
    }
}

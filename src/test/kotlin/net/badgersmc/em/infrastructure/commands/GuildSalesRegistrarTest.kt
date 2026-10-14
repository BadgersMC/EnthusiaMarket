package net.badgersmc.em.infrastructure.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.exceptions.CommandSyntaxException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.command.CommandSender
import kotlin.test.Test
import kotlin.test.assertFailsWith

class GuildSalesRegistrarTest {
    @Test fun `real Brigadier tree retains root arguments defaults exports and ranges`() {
        val service = mockk<GuildSalesCommand>(relaxed = true)
        val sender = mockk<CommandSender>()
        every { sender.hasPermission("enthusiamarket.shop.use") } returns true
        val source = mockk<CommandSourceStack>()
        every { source.sender } returns sender
        val dispatcher = CommandDispatcher<CommandSourceStack>()
        dispatcher.root.addChild(GuildSalesRegistrar.buildTree(service))
        dispatcher.execute("guildsales stall", source)
        dispatcher.execute("guildsales stall today", source)
        dispatcher.execute("guildsales export stall", source)
        dispatcher.execute("guildsales export stall today", source)
        dispatcher.execute("guildsales range stall 2026-10-01 2026-10-10", source)
        dispatcher.execute("guildsales export range stall 2026-10-01 2026-10-10", source)
        verify(exactly = 1) { service.report(sender, "stall", "all") }
        verify(exactly = 1) { service.report(sender, "stall", "today") }
        verify(exactly = 1) { service.exportReport(sender, "stall", "all") }
        verify(exactly = 1) { service.exportReport(sender, "stall", "today") }
        verify(exactly = 1) { service.rangeReport(sender, "stall", "2026-10-01", "2026-10-10") }
        verify(exactly = 1) { service.exportRange(sender, "stall", "2026-10-01", "2026-10-10") }
    }

    @Test fun `permission denial prevents report dispatch`() {
        val service = mockk<GuildSalesCommand>(relaxed = true)
        val sender = mockk<CommandSender>()
        every { sender.hasPermission("enthusiamarket.shop.use") } returns false
        val source = mockk<CommandSourceStack>()
        every { source.sender } returns sender
        val dispatcher = CommandDispatcher<CommandSourceStack>()
        dispatcher.root.addChild(GuildSalesRegistrar.buildTree(service))
        assertFailsWith<CommandSyntaxException> { dispatcher.execute("guildsales stall", source) }
        verify(exactly = 0) { service.report(any(), any(), any()) }
    }
}

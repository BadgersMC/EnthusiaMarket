package net.badgersmc.em.infrastructure.commands

import io.mockk.every
import io.mockk.mockk
import net.badgersmc.em.application.StallAccessIndex
import net.badgersmc.em.domain.stall.Stall
import net.badgersmc.em.domain.stall.StallId
import net.badgersmc.em.domain.stall.StallState
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import kotlin.test.Test
import kotlin.test.assertEquals

class FindStallDirectoryTest {
    @Test fun `last page offers only its public stalls and a return link`() {
        val index = mockk<StallAccessIndex>()
        val stalls = (1..8).map { n ->
            mockk<Stall> {
                every { id } returns StallId("stall_0$n")
                every { state } returns if (n == 1) StallState.MODERATION_HOLD else StallState.UNOWNED
            }
        }
        every { index.currentStalls() } returns stalls
        val lang = mockk<LangService>()
        every { lang.msg(any(), *anyVararg()) } answers { Component.text(firstArg<String>()) }
        val messages = mutableListOf<Component>()
        val player = mockk<Player>()
        every { player.sendMessage(capture(messages)) } returns Unit
        assertEquals(1, FindStallRegistrar.browse(player, index, lang, 2))
        assertEquals(3, messages.size)
        assertEquals(ClickEvent.runCommand("/findstall stall_08"), messages[1].clickEvent())
        assertEquals(ClickEvent.runCommand("/findstall page 1"), messages.last().children().singleOrNull()?.clickEvent()
            ?: messages.last().clickEvent())
    }
}

package net.badgersmc.em.infrastructure.commands

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.mockk.every
import io.mockk.mockk
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.badgersmc.nexus.commands.annotations.Arg
import net.badgersmc.nexus.paper.commands.annotations.Suggests
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.findAnnotation
import kotlin.test.Test
import kotlin.test.assertEquals

class StallCommandSuggestionsTest {
    @Test fun `every registered stall argument has a live provider`() {
        for ((type, provider) in listOf(AdminCommands::class to "stallIds", StallAccessCommands::class to "managedStalls")) {
            val args = type.declaredMemberFunctions.flatMap { it.parameters }.filter { it.findAnnotation<Arg>()?.name == "stall" }
            kotlin.test.assertTrue(args.isNotEmpty())
            val allowed = if (provider == "stallIds") setOf(provider, "ownerStalls", "emergencyStalls", "buyOffers", "cancelOffers") else setOf(provider)
            args.forEach { kotlin.test.assertTrue(it.findAnnotation<Suggests>()?.value in allowed) }
        }
    }

    @Test fun `empty and partial input replace only the active argument and see current IDs`() {
        val ids = listOf("stall_cm_08", "stall_cm_18")
        val repository = mockk<net.badgersmc.em.domain.stall.StallRepository>()
        every { repository.all() } returns ids.map { id ->
            mockk<net.badgersmc.em.domain.stall.Stall> {
                every { this@mockk.id } returns net.badgersmc.em.domain.stall.StallId(id)
                every { world } returns "world"
                every { regionId } returns id
            }
        }
        val index = net.badgersmc.em.application.StallAccessIndex(repository)
        index.rebuild()
        val provider = StallCommandSuggestions.stallIds(index)
        val context = mockk<CommandContext<CommandSourceStack>>()
        every { context.source } returns mockk()
        val prefix = "em stall info "
        fun complete(fragment: String) = provider.getSuggestions(context, SuggestionsBuilder(prefix + fragment, prefix.length))
            .join().list.map { it.apply(prefix + fragment) }
        assertEquals(ids.map { prefix + it }, complete(""))
        index.invalidate(ids.first())
        assertEquals(listOf(prefix + "stall_cm_18"), complete("STALL_CM_"))
        assertEquals(emptyList(), complete("missing"))
    }
}

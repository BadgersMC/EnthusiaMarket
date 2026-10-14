package net.badgersmc.em.infrastructure.commands

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import net.badgersmc.nexus.core.NexusContext
import org.bukkit.plugin.Plugin

/** Preserve root arguments using Paper directly, as Nexus requires nonempty literal paths. */
internal object GuildSalesRegistrar {
    fun register(plugin: Plugin, nexus: NexusContext) {
        plugin.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            event.registrar().register(buildTree(nexus.getBean(GuildSalesCommand::class)),
                "Guild stall contribution and gross sales report", listOf("stallsales"))
        }
    }

    fun buildTree(service: GuildSalesCommand): LiteralCommandNode<CommandSourceStack> {
        val root = literal("guildsales").requires { it.sender.hasPermission("enthusiamarket.shop.use") }
        root.then(reportArguments(service, false))
        root.then(rangeArguments(service, false))
        root.then(literal("export").then(reportArguments(service, true)).then(rangeArguments(service, true)))
        return root.build()
    }

    private fun reportArguments(service: GuildSalesCommand, export: Boolean): RequiredArgumentBuilder<CommandSourceStack, String> =
        argument("stall").executes { runReport(it, service, export, "all") }
            .then(argument("period").executes { runReport(it, service, export, text(it, "period")) })

    private fun runReport(context: CommandContext<CommandSourceStack>, service: GuildSalesCommand, export: Boolean, period: String): Int {
        val sender = context.source.sender
        if (export) service.exportReport(sender, text(context, "stall"), period)
        else service.report(sender, text(context, "stall"), period)
        return 1
    }

    private fun rangeArguments(service: GuildSalesCommand, export: Boolean): LiteralArgumentBuilder<CommandSourceStack> =
        literal("range").then(argument("stall").then(argument("from").then(argument("to").executes { context ->
            val sender = context.source.sender
            if (export) service.exportRange(sender, text(context, "stall"), text(context, "from"), text(context, "to"))
            else service.rangeReport(sender, text(context, "stall"), text(context, "from"), text(context, "to"))
            1
        })))

    private fun literal(name: String) = LiteralArgumentBuilder.literal<CommandSourceStack>(name)
    private fun argument(name: String) = RequiredArgumentBuilder.argument<CommandSourceStack, String>(name, StringArgumentType.word())
    private fun text(context: CommandContext<CommandSourceStack>, name: String) = StringArgumentType.getString(context, name)
}

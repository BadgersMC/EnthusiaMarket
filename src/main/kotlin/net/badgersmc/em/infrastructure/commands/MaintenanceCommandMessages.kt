package net.badgersmc.em.infrastructure.commands

import net.badgersmc.em.application.MaintenanceFreezeResult
import net.badgersmc.em.application.MaintenanceFreezeService
import net.badgersmc.nexus.i18n.LangService
import org.bukkit.command.CommandSender
import java.time.Duration
import java.time.Instant

/** Formats the player-facing responses for maintenance timer controls. */
internal class MaintenanceCommandMessages(
    private val lang: LangService,
    private val freeze: MaintenanceFreezeService,
) {
    fun freeze(sender: CommandSender) {
        val message = when (val result = freeze.begin()) {
            is MaintenanceFreezeResult.AlreadyActive -> lang.msg(
                "admin.maintenance.freeze.already_active", "since" to result.since.toString(),
            )
            else -> lang.msg("admin.maintenance.freeze.activated")
        }
        sender.sendMessage(message)
    }

    fun unfreeze(sender: CommandSender) {
        val message = when (val result = freeze.end()) {
            is MaintenanceFreezeResult.Lifted -> lang.msg(
                "admin.maintenance.unfreeze.done", "stalls" to result.stalls,
                "auctions" to result.auctions, "duration" to formatDuration(result.elapsed),
            )
            else -> lang.msg("admin.maintenance.unfreeze.not_frozen")
        }
        sender.sendMessage(message)
    }

    fun status(sender: CommandSender) {
        val status = freeze.status()
        val message = if (status.frozen && status.since != null) {
            lang.msg(
                "admin.maintenance.status.active", "since" to status.since.toString(),
                "duration" to formatDuration(Duration.between(status.since, Instant.now()).coerceAtLeast(Duration.ZERO)),
            )
        } else lang.msg("admin.maintenance.status.inactive")
        sender.sendMessage(message)
    }

    private fun formatDuration(duration: Duration): String = buildString {
        val days = duration.toDays()
        val hours = duration.toHours() % 24
        if (days > 0) append("${days}d ")
        if (hours > 0 || days > 0) append("${hours}h ")
        append("${duration.toMinutes() % 60}m")
    }
}

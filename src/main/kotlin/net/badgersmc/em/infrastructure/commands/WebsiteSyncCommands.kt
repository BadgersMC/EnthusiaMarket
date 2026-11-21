package net.badgersmc.em.infrastructure.commands

import java.time.Instant
import net.badgersmc.em.websync.WebsiteSyncService
import org.bukkit.command.CommandSender

internal class WebsiteSyncCommands(private val websiteSync: WebsiteSyncService?) {
    fun webSyncStatus(sender: CommandSender) {
        val status = sync().status()
        sender.sendMessage("Website sync: configured=${yesNo(status.configuredEnabled)}, active=${yesNo(status.active)}, " +
            "secret=${yesNo(status.secretConfigured)}, validation=${status.validation}")
        sender.sendMessage("Pending stalls=${status.pendingStalls}, full=${yesNo(status.pendingFull)}, " +
            "oldest=${age(status.oldestPendingAgeMillis)}, snapshot=${status.snapshotRevision}")
        sender.sendMessage("Last full=${instant(status.lastFullSuccess)}, last stall=${instant(status.lastStallSuccess)}, " +
            "status=${status.errorCategory ?: "ok"}")
        status.bedrockHeads?.let { heads ->
            sender.sendMessage("Bedrock heads: geyser=${yesNo(status.geyserApiAvailable)}, " +
                "capture=${yesNo(status.bedrockCaptureEnabled)}, published=${heads.captured}, pending=${heads.pending}, " +
                "last=${instant(heads.lastSuccessAt)}, status=${heads.lastError ?: "ok"}")
        }
        status.floodgateCapture?.let { capture ->
            sender.sendMessage("Floodgate capture: events=${capture.events}, accepted=${capture.accepted}, queued=${capture.queued}, " +
                "rejected=${capture.rejected}, failed=${capture.failed}, dropped=${capture.dropped}, " +
                "last=${capture.lastFailure ?: "ok"}")
        }
    }

    fun webSyncSecret(sender: CommandSender, value: WebsiteSyncSecretArgument) {
        val result = sync().setSecret(value.value)
        sender.sendMessage(if (result.config != null) "Website sync secret stored; synchronization remains in its configured state."
            else "Website sync configuration is invalid; synchronization is disabled.")
    }

    fun webSyncClearSecret(sender: CommandSender, confirmation: String) {
        if (confirmation != "confirm") {
            sender.sendMessage("Use /em websync clear-secret confirm")
            return
        }
        sync().clearSecret()
        sender.sendMessage("Website sync secret cleared and synchronization disabled.")
    }

    fun webSyncTest(sender: CommandSender) {
        sender.sendMessage("Testing website sync authentication...")
        sync().authenticatedTest { success, category ->
            sender.sendMessage(if (success) "Website sync authentication succeeded." else "Website sync test failed: $category")
        }
    }

    fun webSyncValidate(sender: CommandSender) {
        val report = sync().validateLiveReport()
        if (report.errors.isEmpty()) sender.sendMessage("Website sync validation passed for all 71 exact stall identities.")
        else sender.sendMessage("Website sync validation failed (${report.errors.size} safe issue(s)): ${report.errors.take(10).joinToString()}")
        sender.sendMessage("Website sync diagnostics: ${report.diagnostics.joinToString()}")
    }

    fun webSyncFull(sender: CommandSender) {
        sender.sendMessage(if (sync().requestFull()) "Website full reconciliation requested." else "Website sync is not active.")
    }

    fun webSyncEnable(sender: CommandSender) {
        sync().enable()
        sender.sendMessage(if (sync().status().active) "Website sync enabled; full reconciliation started."
            else "Website sync remains inactive until a valid secret, configuration, and persistence are present.")
    }

    fun webSyncDisable(sender: CommandSender) {
        sync().disable()
        sender.sendMessage("Website sync disabled; persistent pending state was retained.")
    }

    fun webSyncRetry(sender: CommandSender) {
        sender.sendMessage(if (sync().retry()) "Website sync delivery retry requested." else "Website sync retry could not be queued.")
    }

    private fun sync(): WebsiteSyncService = requireNotNull(websiteSync) { "Website sync service is unavailable" }
}

private fun yesNo(value: Boolean) = if (value) "yes" else "no"
private fun age(value: Long?): String = value?.let { "${it / 1000}s" } ?: "none"
private fun instant(value: Long?): String = value?.let { Instant.ofEpochMilli(it).toString() } ?: "never"

package net.badgersmc.em.infrastructure.lumaguilds

import net.badgersmc.em.domain.ports.GuildProvider
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.lumalyte.lg.api.GuildSummary

private val MINI_TAG = Regex("<[^>]+>")

/** Map a LumaGuilds [GuildSummary] to [GuildProvider.GuildRef], normalising tag/emoji to MiniMessage. */
internal fun toGuildRef(guild: GuildSummary): GuildProvider.GuildRef =
    GuildProvider.GuildRef(
        guild.id.toString(),
        guild.name,
        normalizeToMiniMessage(guild.tag),
        normalizeToMiniMessage(guild.emoji),
    )

/**
 * Normalise a stored guild tag/emoji to MiniMessage so EM's sign renderer
 * (which parses placeholder values as MiniMessage) colours it correctly.
 *
 * LumaGuilds stores tags in whatever format the setter passed: the Java
 * tag-editor menu converts legacy `&` codes to MiniMessage on save, but the
 * `/guild tag` command and the Bedrock editor store the raw input — so
 * legacy-coded tags exist in the wild. Mirror LumaGuilds' own
 * ColorCodeUtils.convertLegacyToMiniMessage: pass MiniMessage through
 * unchanged, convert legacy `&` codes, and never throw.
 */
private fun normalizeToMiniMessage(raw: String?): String {
    val value = raw ?: return ""
    if (value.isEmpty()) return ""
    // Already MiniMessage (contains a tag) — leave it alone.
    if (value.contains(MINI_TAG)) return value
    // No legacy codes either — plain text, nothing to convert.
    if (!value.contains('&') && !value.contains('§')) return value
    return try {
        val legacy = if (value.contains('§')) {
            LegacyComponentSerializer.legacySection()
        } else {
            LegacyComponentSerializer.legacyAmpersand()
        }
        MiniMessage.miniMessage().serialize(legacy.deserialize(value))
    } catch (_: Exception) {
        value
    }
}

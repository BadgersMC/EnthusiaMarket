package net.badgersmc.em.interaction

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor

/** Balanced colors for Market text rendered outside the bundled MiniMessage locale. */
internal object MarketText {
    val copper = TextColor.color(0xF2B66D)
    val cream = TextColor.color(0xFFF6E0)
    val gold = TextColor.color(0xFFCF1F)
    val sage = TextColor.color(0xB6D7A8)
    val crimson = TextColor.color(0xC0261D)
    val error = TextColor.color(0xFFD0C6)
    val muted = TextColor.color(0xF0E5CD)

    fun bedrock(label: String, color: TextColor): String =
        LegacyComponentSerializer.legacySection().serialize(Component.text(label, color))
}

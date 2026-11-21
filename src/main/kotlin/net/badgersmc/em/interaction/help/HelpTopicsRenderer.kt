package net.badgersmc.em.interaction.help

import net.badgersmc.em.interaction.MarketText
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.TextDecoration

/**
 * Builds Adventure components for the in-game `/em help` UI.
 *
 * Two public surfaces:
 *  - [renderTopicMenu] - the top-level topic picker (no argument).
 *  - [renderTopicPage] - one topic's command list (`/em help <topic>`).
 *
 * All click events use `RUN_COMMAND` (for topic switches) or
 * `SUGGEST_COMMAND` (for command prefill) so Geyser maps them to taps.
 */
object HelpTopicsRenderer {

    fun renderTopicMenu(): Component {
        val header = Component.text("─── EnthusiaMarket Help ───", MarketText.gold, TextDecoration.BOLD)
        val intro = Component.text("Pick a topic (click or type /em help topic <topic>):", MarketText.cream)

        val entries = HelpTopics.all.map { topic -> renderTopicEntry(topic) }

        val wikiLink = Component.text()
            .append(Component.text("Full wiki: ", MarketText.cream))
            .append(
                Component.text(HelpTopics.WIKI_BASE_URL, MarketText.gold, TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.openUrl(HelpTopics.WIKI_BASE_URL))
                    .hoverEvent(HoverEvent.showText(Component.text("Open in browser", MarketText.gold))),
            )
            .build()

        val out = Component.text()
            .append(header).append(Component.newline())
            .append(intro).append(Component.newline())
        entries.forEach { out.append(it).append(Component.newline()) }
        out.append(wikiLink)
        return out.build()
    }

    private fun renderTopicEntry(topic: HelpTopic): Component {
        return Component.text()
            .append(Component.text("  [", MarketText.muted))
            .append(Component.text(topic.displayName, MarketText.gold))
            .append(Component.text("] ", MarketText.muted))
            .append(Component.text(topic.summary, MarketText.cream))
            .clickEvent(ClickEvent.runCommand("/em help topic ${topic.slug}"))
            .hoverEvent(HoverEvent.showText(Component.text("Open ${topic.displayName} help", MarketText.gold)))
            .build()
    }

    fun renderTopicPage(topic: HelpTopic): Component {
        val header = Component.text("─── Help · ${topic.displayName} ───", MarketText.gold, TextDecoration.BOLD)
        val summary = Component.text(topic.summary, MarketText.cream)

        val commandLines = topic.commands.map { entry -> renderCommandEntry(entry) }

        val wikiUrl = "${HelpTopics.WIKI_BASE_URL}/${topic.slug}/"
        val wikiLine = Component.text()
            .append(Component.text("Read more: ", MarketText.cream))
            .append(
                Component.text(wikiUrl, MarketText.gold, TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.openUrl(wikiUrl))
                    .hoverEvent(HoverEvent.showText(Component.text("Open in browser", MarketText.gold))),
            )
            .build()

        val back = Component.text("[Back to topics]", MarketText.gold)
            .clickEvent(ClickEvent.runCommand("/em help"))
            .hoverEvent(HoverEvent.showText(Component.text("Open the topic menu", MarketText.gold)))

        val out = Component.text()
            .append(header).append(Component.newline())
            .append(summary).append(Component.newline())
            .append(Component.text("How to:", MarketText.gold)).append(Component.newline())
        commandLines.forEach { out.append(it).append(Component.newline()) }
        out.append(Component.newline())
            .append(wikiLine).append(Component.newline())
            .append(back)
        return out.build()
    }

    private fun renderCommandEntry(entry: HelpCommandEntry): Component {
        val line = Component.text()
            .append(Component.text("  ", MarketText.muted))
            .append(Component.text(entry.syntax, MarketText.cream))
        if (entry.prefill.isNotEmpty()) {
            line.clickEvent(ClickEvent.suggestCommand(entry.prefill))
                .hoverEvent(
                    HoverEvent.showText(
                        Component.text()
                            .append(Component.text(entry.blurb, MarketText.gold))
                            .append(Component.newline())
                            .append(Component.text("Click to prefill in chat", MarketText.cream))
                            .build(),
                    ),
                )
        } else {
            line.hoverEvent(HoverEvent.showText(Component.text(entry.blurb, MarketText.gold)))
        }
        return line.build()
    }
}

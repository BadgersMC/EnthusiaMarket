package net.badgersmc.em.infrastructure.commands

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import io.papermc.paper.command.brigadier.CommandSourceStack
import java.nio.file.Path
import net.badgersmc.em.application.AuctionLifecycleService
import net.badgersmc.em.application.EntityLimitConfig
import net.badgersmc.em.application.StallAccessIndex
import net.badgersmc.em.application.StallAccessSettingsService
import net.badgersmc.em.config.EnthusiaMarketConfig
import net.badgersmc.em.domain.auction.AuctionId
import net.badgersmc.em.domain.auction.AuctionRepository
import net.badgersmc.em.domain.auction.AuctionState
import net.badgersmc.em.domain.offer.SellOfferRepository
import net.badgersmc.em.domain.ports.GuildProvider
import net.badgersmc.nexus.core.NexusContext
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player

internal object StallCommandSuggestions {
    fun choices(values: (CommandSourceStack) -> Iterable<String>): SuggestionProvider<CommandSourceStack> =
        contextual { values(it.source) }

    private fun contextual(values: (CommandContext<CommandSourceStack>) -> Iterable<String>): SuggestionProvider<CommandSourceStack> =
        SuggestionProvider { context, builder ->
            values(context).filter { it.startsWith(builder.remaining, ignoreCase = true) }
                .distinct().forEach(builder::suggest)
            builder.buildFuture()
        }

    fun stallIds(index: StallAccessIndex) = choices { index.currentStalls().map { it.id.value } }

    fun providers(
        nexus: NexusContext, index: StallAccessIndex, lotDirectory: Path, config: EnthusiaMarketConfig,
    ): Map<String, SuggestionProvider<CommandSourceStack>> {
        val entityNames = EntityType.entries.filter { it.isSpawnable }.map { it.name.lowercase() }
        val configuredKinds = EntityLimitConfig.load(lotDirectory.resolveSibling("entitylimits.yml").toFile()).keys
        return mapOf(
            "stallIds" to stallIds(index),
            "managedStalls" to choices { source ->
                val player = source.sender as? Player ?: return@choices emptyList()
                val settings = nexus.getBean(StallAccessSettingsService::class)
                index.currentStalls().filter { settings.mayManage(it, player.uniqueId) }.map { it.id.value }
            },
            "ownerStalls" to choices { source ->
                val player = source.sender as? Player ?: return@choices emptyList()
                val guilds = nexus.getBean(GuildProvider::class)
                index.currentStalls().filter { it.canManage(player.uniqueId, guilds) }.map { it.id.value }
            },
            "buyOffers" to choices { source ->
                val player = source.sender as? Player ?: return@choices emptyList()
                nexus.getBean(SellOfferRepository::class).all().filter { it.sellerUuid != player.uniqueId }.map { it.stallId.value }
            },
            "cancelOffers" to choices { source ->
                val player = source.sender as? Player ?: return@choices emptyList()
                val guilds = nexus.getBean(GuildProvider::class)
                nexus.getBean(SellOfferRepository::class).all().filter {
                    it.sellerUuid == player.uniqueId || index.cached(it.stallId.value)?.canManage(player.uniqueId, guilds) == true
                }.map { it.stallId.value }
            },
            "nextBid" to contextual { context ->
                val id = context.getArgument("auction", String::class.java)
                val auction = nexus.getBean(AuctionLifecycleService::class).auctionForBid(AuctionId(id)) ?: return@contextual emptyList()
                val high = auction.highBid?.amount
                if (auction.state != AuctionState.OPEN || high == Long.MAX_VALUE) return@contextual emptyList()
                listOf((high?.plus(1) ?: auction.startingBid).toString())
            },
            "stallKinds" to choices { index.currentStalls().map { it.kind } + configuredKinds + "default" },
            "visiblePlayers" to choices { source ->
                val viewer = source.sender as? Player
                source.sender.server.onlinePlayers.filter { viewer == null || viewer.canSee(it) }.map { it.name }
            },
            "rosterMembers" to contextual { context ->
                val player = context.source.sender as? Player ?: return@contextual emptyList()
                val stall = index.cached(context.getArgument("stall", String::class.java)) ?: return@contextual emptyList()
                if (!index.isFresh(stall.id.value) || !stall.canManage(player.uniqueId, nexus.getBean(GuildProvider::class))) {
                    return@contextual emptyList()
                }
                stall.members.mapNotNull { uuid ->
                    val online = player.server.getPlayer(uuid)
                    if (online == null) player.server.getOfflinePlayer(uuid).name else online.name.takeIf { player.canSee(online) }
                }
            },
            "bidGuilds" to choices { source ->
                val player = source.sender as? Player ?: return@choices emptyList()
                nexus.getBean(AuctionLifecycleService::class).eligibleGuilds(player.uniqueId).map { it.id }
            },
            "openAuctionIds" to choices { nexus.getBean(AuctionRepository::class).allOpen().map { it.id.value } },
            "directoryPages" to choices {
                val last = (index.currentStalls().size - 1) / AdminCommands.LIST_PAGE_SIZE + 1
                (1..last).map(Int::toString)
            },
            "lotFiles" to choices { AuctionLotImport.filenames(lotDirectory) },
            "roundExamples" to choices { listOf(config.auction.openingRoundId.ifBlank { "opening" }) },
            "previewToken" to choices { source ->
                val actor = runCatching { AuctionLotImport.actor(source.sender) }.getOrNull()
                listOfNotNull(actor?.let { nexus.getBean(AuctionLotImport::class).currentToken(it) })
            },
            "entityTypes" to choices { entityNames },
            "auctionDurations" to choices { listOf(config.auction.minDuration, config.auction.defaultDuration, config.auction.maxDuration) },
            "rentDurations" to choices { listOf("1h", "1d", "7d") },
            "booleans" to choices { listOf("true", "false") },
            "outlineSeconds" to choices { listOf("10", "30", "60") },
            "extraCounts" to choices { listOf("0", "1", "5") },
            "startingBids" to choices { listOf(config.auction.minStartingBid.coerceAtLeast(1).toString()) },
            "goldExamples" to choices { listOf("10", "50", "100") },
        )
    }
}

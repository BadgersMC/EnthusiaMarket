package net.badgersmc.em.infrastructure.listeners

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldguard.WorldGuard
import net.badgersmc.em.domain.shop.ShopRepository
import net.badgersmc.em.domain.stall.OwnerType
import net.badgersmc.em.domain.stall.Stall
import net.badgersmc.em.domain.stall.StallRepository
import net.badgersmc.em.domain.ports.GuildProvider
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.annotations.Component
import org.bukkit.Location
import org.bukkit.block.Container
import org.bukkit.block.Sign
import org.bukkit.block.data.type.WallSign
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.Event
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack

/**
 * Listens for player left-click+sneak on wall signs attached to containers
 * inside owned stalls, opening the CreateShopMenu (REQ-012).
 */
@net.badgersmc.nexus.paper.listeners.Listener
@Component
open class ShopCreateListener(
    private val stallRepository: StallRepository,
    private val shopRepository: ShopRepository,
    private val lang: LangService,
    private val menuFactory: net.badgersmc.em.interaction.MenuFactory,
    private val shopSignRenderer: net.badgersmc.em.application.ShopSignRenderer,
    private val guildProvider: GuildProvider? = null,
    private val access: net.badgersmc.em.domain.ports.StallAccessPolicy = net.badgersmc.em.domain.ports.StallAccessPolicy.Open,
) : Listener {

    private val logger = java.util.logging.Logger.getLogger(ShopCreateListener::class.java.name)

    @EventHandler
    fun onSignInteract(event: PlayerInteractEvent) {
        val target = creationTarget(event) ?: return
        val block = target.first
        val attachedBlock = target.second
        val loc = block.location

        // Check the sign is inside an owned stall
        val stall = findStallAt(loc) ?: run {
            event.player.sendMessage(lang.msg("shop.create.not_in_stall"))
            return
        }

        // Check player can manage this stall
        if (!canManageStall(stall, event.player) || findStallAt(attachedBlock.location)?.id != stall.id) {
            event.player.sendMessage(lang.msg("shop.create.no_authority"))
            return
        }

        event.setUseInteractedBlock(Event.Result.DENY)

        // Capture the held item as the sell item (REQ-012).
        val sellItemB64 = captureSellItem(event.player.inventory.itemInMainHand)
        if (sellItemB64 == null) {
            event.player.sendMessage(lang.msg("shop.create.no_held_item"))
            return
        }

        showCreationMenu(event.player, stall, target, sellItemB64)
    }

    private fun showCreationMenu(player: Player, stall: Stall, target: Pair<org.bukkit.block.Block, org.bukkit.block.Block>, sellItemB64: String) {
        val signLoc = target.first.location
        val containerLoc = target.second.location
        val authorised: (Player) -> Boolean = { actor ->
            mayStillCreate(stall, signLoc, containerLoc, actor)
        }
        if (menuFactory.shouldUseBedrockMenus(player)) {
            net.badgersmc.em.interaction.bedrock.BedrockCreateShopForm(
                player, player.uniqueId, stall.id.value, signLoc, containerLoc,
                sellItemB64, shopRepository, logger, lang, shopSignRenderer,
                authorised,
            ).open(player)
        } else {
            net.badgersmc.em.interaction.gui.CreateShopMenu(
                stall.id.value, player.uniqueId, signLoc, containerLoc,
                sellItemB64, shopRepository, lang,
                authorised = authorised,
            ).open(player)
        }
    }

    private fun mayStillCreate(stall: Stall, sign: Location, container: Location, actor: Player): Boolean {
        val current = stallRepository.findById(stall.id) ?: return false
        if (current.state !in setOf(net.badgersmc.em.domain.stall.StallState.OWNED, net.badgersmc.em.domain.stall.StallState.GRACE)) return false
        return findStallAt(sign)?.id == current.id && findStallAt(container)?.id == current.id && canManageStall(current, actor)
    }

    private fun interactionBlock(event: PlayerInteractEvent): org.bukkit.block.Block? {
        if (event.hand != org.bukkit.inventory.EquipmentSlot.HAND) return null
        if (!event.player.isSneaking) return null
        return when (event.action) {
            Action.LEFT_CLICK_BLOCK -> event.clickedBlock
            Action.LEFT_CLICK_AIR -> spearTarget(event.player)
            else -> null
        }
    }

    private fun spearTarget(player: Player): org.bukkit.block.Block? =
        if (player.inventory.itemInMainHand.type.name.endsWith("_SPEAR")) player.getTargetBlockExact(6) else null

    private fun creationTarget(event: PlayerInteractEvent): Pair<org.bukkit.block.Block, org.bukkit.block.Block>? {
        val block = interactionBlock(event) ?: return null
        val wallSignData = wallSign(block) ?: return null

        // Must not already be a registered shop
        val loc = block.location
        if (registeredShopAt(loc)) {
            event.player.sendMessage(lang.msg("shop.create.already_shop"))
            return null
        }

        // Find attached container via the sign's attached block face
        val facing = wallSignData.facing
        val attachedBlock = block.getRelative(facing.oppositeFace)

        if (attachedBlock.state !is Container) {
            event.player.sendMessage(lang.msg("shop.create.needs_container"))
            return null
        }

        return block to attachedBlock
    }

    private fun wallSign(block: org.bukkit.block.Block): WallSign? =
        if (block.state is Sign) block.blockData as? WallSign else null

    private fun registeredShopAt(loc: Location): Boolean =
        shopRepository.findBySign(loc.world?.name ?: "world", loc.blockX, loc.blockY, loc.blockZ) != null

    companion object {
        /**
         * Capture the player's main-hand item as a base64 sell item, or null
         * when the hand is empty. Amount is normalised to 1 (per-trade amount
         * is chosen in the menu). Mirrors SignPlaceListener.
         */
        fun captureSellItem(held: ItemStack): String? {
            if (held.type == org.bukkit.Material.AIR || held.amount <= 0) return null
            val one = held.clone().apply { amount = 1 }
            return net.badgersmc.em.application.ItemStackSerializer.serialize(one)
        }
    }

    open fun findStallAt(location: Location): Stall? {
        val world = location.world ?: return null
        val wgWorld = BukkitAdapter.adapt(world)
        val container = WorldGuard.getInstance().platform.regionContainer
        val regionManager = container.get(wgWorld) ?: return null
        val regions = regionManager.getApplicableRegions(BukkitAdapter.asBlockVector(location))
        for (region in regions) {
            val stall = stallRepository.findByRegion(world.name, region.id)
            if (stall != null) return stall
        }
        return null
    }

    open fun canManageStall(stall: Stall, player: Player): Boolean {
        if (player.hasPermission("enthusiamarket.admin")) return true
        return when (stall.owner.type) {
            OwnerType.SOLO -> stall.owner.id == player.uniqueId.toString() ||
                player.uniqueId in stall.members
            OwnerType.GUILD -> {
                val provider = guildProvider ?: run {
                    player.sendMessage(lang.msg("shop.create.guild_unavailable"))
                    return false
                }
                access.allows(stall.id.value, player.uniqueId, net.badgersmc.em.domain.stall.StallCapability.STOCK) &&
                    net.badgersmc.em.domain.stall.GuildStallAccessRules.allows(
                        stall, player.uniqueId, provider, GuildProvider.GuildPermission.MANAGE_SHOPS,
                    )
            }
            OwnerType.NONE -> false
        }
    }
}

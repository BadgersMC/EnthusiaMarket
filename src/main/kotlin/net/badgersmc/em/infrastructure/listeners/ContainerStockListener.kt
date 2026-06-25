package net.badgersmc.em.infrastructure.listeners

import net.badgersmc.em.application.ItemStackSerializer
import net.badgersmc.em.domain.shop.Shop
import net.badgersmc.em.domain.shop.ShopRepository
import net.badgersmc.em.events.PostShopTransactionEvent
import net.badgersmc.em.events.ShopStockDepletedEvent
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.annotations.Component
import org.bukkit.Bukkit
import org.bukkit.block.Container
import org.bukkit.block.Sign
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.inventory.Inventory

/**
 * Keeps shop sign stock text in sync with linked container inventories.
 *
 * **Trade path** — [onTransaction] fires after every successful [PostShopTransactionEvent]:
 * recomputes raw stock, persists [ShopRepository.updateStock], and updates the sign.
 *
 * **Timer path** — [refreshAllSigns] is called every 20 ticks from [EnthusiaMarket.onEnable].
 * Iterates all shops, reads the live container inventory for loaded chunks only (never
 * force-loads), and updates sign + denormalized stock_count when the raw stock changes.
 * This catches stock drift from shift-click, hopper, or other-plugin inventory mutations
 * without needing per-event listeners.
 */
@net.badgersmc.nexus.paper.listeners.Listener
@Component
class ContainerStockListener(
    private val shopRepository: ShopRepository,
    private val lang: LangService
) : Listener {

    /** shopId → last-persisted raw stock (dedup: skip sign update if unchanged). */
    private val lastRawStock: MutableMap<Long, Int> = mutableMapOf()
    private var previouslyDepletedShops: MutableSet<Long> = mutableSetOf()

    // ── Trade path ──────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onTransaction(event: PostShopTransactionEvent) {
        val shop = shopRepository.findById(event.shopId) ?: return
        val container = loadedContainer(shop) ?: return
        val rawStock = rawStockOf(container.inventory, shop)
        val trades = rawStock / shop.sellAmount.coerceAtLeast(1)
        lastRawStock[shop.id] = rawStock
        shopRepository.updateStock(shop.id, rawStock)
        val sign = loadedSign(shop) ?: return
        updateSignStock(sign, trades)
        trackDepletion(shop, trades)
    }

    // ── Timer path (called from EnthusiaMarket.onEnable every 20t) ─────

    /** Recompute stock for every shop whose container chunk is loaded. */
    fun refreshAllSigns() {
        for (shop in shopRepository.all()) {
            val inventory = containerInventoryIfLoaded(shop) ?: continue
            refreshOne(shop, inventory)
        }
    }

    /** Recompute + persist + sign-update for a single shop whose inventory is known to be loaded. */
    private fun refreshOne(shop: Shop, inventory: Inventory) {
        val rawStock = rawStockOf(inventory, shop)
        if (rawStock == lastRawStock[shop.id]) return                      // unchanged → skip
        lastRawStock[shop.id] = rawStock

        val trades = rawStock / shop.sellAmount.coerceAtLeast(1)
        shopRepository.updateStock(shop.id, rawStock)
        val sign = loadedSign(shop) ?: return
        updateSignStock(sign, trades)
        trackDepletion(shop, trades)
    }

    /** The shop's container inventory, or null if the world/chunk/block is unavailable. */
    private fun containerInventoryIfLoaded(shop: Shop): Inventory? {
        val world = Bukkit.getWorld(shop.containerWorld) ?: return null
        if (!world.isChunkLoaded(shop.containerX shr 4, shop.containerZ shr 4)) return null
        val container = world.getBlockAt(shop.containerX, shop.containerY, shop.containerZ)
            .state as? Container ?: return null
        return container.inventory
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun rawStockOf(inventory: Inventory, shop: Shop): Int {
        val sellStack = ItemStackSerializer.deserialize(shop.sellItem) ?: return 0
        return inventory.contents.filterNotNull()
            .filter { it.isSimilar(sellStack) }
            .sumOf { it.amount }
    }

    private fun loadedContainer(shop: Shop): Container? {
        val world = Bukkit.getWorld(shop.containerWorld) ?: return null
        return world.getBlockAt(shop.containerX, shop.containerY, shop.containerZ)
            .state as? Container
    }

    private fun loadedSign(shop: Shop): Sign? {
        val world = Bukkit.getWorld(shop.signWorld) ?: return null
        return world.getBlockAt(shop.signX, shop.signY, shop.signZ)
            .state as? Sign
    }

    private fun updateSignStock(state: Sign, trades: Int) {
        state.line(3, lang.msg("container_sign.stock_line", "trades" to trades))
        state.update(true)
    }

    private fun trackDepletion(shop: Shop, trades: Int) {
        if (trades == 0) {
            if (shop.id !in previouslyDepletedShops) {
                previouslyDepletedShops.add(shop.id)
                Bukkit.getPluginManager().callEvent(ShopStockDepletedEvent(shop.owner))
            }
        } else {
            previouslyDepletedShops.remove(shop.id)
        }
    }
}

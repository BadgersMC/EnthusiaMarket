package net.badgersmc.em.application

import io.mockk.mockk
import net.badgersmc.em.domain.ports.EconomyProvider
import net.badgersmc.em.domain.ports.GuildProvider
import net.badgersmc.em.domain.shop.Shop
import org.bukkit.block.Container
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

typealias InventoryPredicate = (Inventory, ItemStack, Int) -> Boolean

val inventoryNeverHas: InventoryPredicate = { _, _, _ -> false }
val inventoryNeverFits: InventoryPredicate = { _, _, _ -> false }
val inventoryAlwaysHas: InventoryPredicate = { _, _, _ -> true }
val inventoryFitsWhenPositive: InventoryPredicate = { _, _, amount -> amount > 0 }

fun inventoryFailsExcept(containerInv: Inventory): InventoryPredicate =
    { inv, _, _ -> inv != containerInv }

/** Named test seam over [ContainerTradeService] — avoids Codacy anonymous-class limits. */
open class ContainerTradeServiceHarness(
    stallRepo: net.badgersmc.em.domain.stall.StallRepository,
    economy: EconomyProvider = mockk(relaxed = true),
    guildProvider: GuildProvider? = null,
    tradePolicy: GuildTradePolicyService? = null,
    shopVault: ShopVaultService? = null,
    private val mockItemStack: ItemStack = mockk(relaxed = true),
    private val mockContainer: Container? = mockk(relaxed = true),
    private val hasAtLeast: InventoryPredicate = inventoryAlwaysHas,
    private val canFit: InventoryPredicate = inventoryFitsWhenPositive,
) : ContainerTradeService(stallRepo, economy, guildProvider, tradePolicy, shopVault) {
    override fun deserializeStack(base64: String): ItemStack? = mockItemStack
    override fun getContainer(shop: Shop): Container? = mockContainer
    override fun inventoryHasAtLeast(inventory: Inventory, template: ItemStack, amount: Int): Boolean =
        hasAtLeast(inventory, template, amount)
    override fun inventoryCanFit(inventory: Inventory, template: ItemStack, amount: Int): Boolean =
        canFit(inventory, template, amount)
}
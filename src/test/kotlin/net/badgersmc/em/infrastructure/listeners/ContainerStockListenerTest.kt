package net.badgersmc.em.infrastructure.listeners

import io.mockk.*
import net.badgersmc.em.application.ItemStackSerializer
import net.badgersmc.em.domain.shop.Shop
import net.badgersmc.em.domain.shop.ShopRepository
import net.kyori.adventure.text.Component
import net.badgersmc.em.events.PostShopTransactionEvent
import net.badgersmc.em.events.ShopStockDepletedEvent
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.Container
import org.bukkit.block.Sign
import org.bukkit.event.Event
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.PluginManager
import org.junit.jupiter.api.AfterEach
import java.util.UUID
import kotlin.test.Test

class ContainerStockListenerTest {

    @AfterEach
    fun cleanupMocks() {
        unmockkAll()
    }

    /** Creates a shop with the given coordinates. */
    private fun shop(
        signX: Int = 100, signY: Int = 64, signZ: Int = 200,
        contX: Int = 50, contY: Int = 64, contZ: Int = 60,
        sellAmount: Int = 1,
        owner: UUID = UUID.randomUUID()
    ): Shop = Shop(
        id = 1L, stallId = "s1", owner = owner,
        signWorld = "world", signX = signX, signY = signY, signZ = signZ,
        containerWorld = "world", containerX = contX, containerY = contY, containerZ = contZ,
        sellItem = "base64item", sellAmount = sellAmount,
        costItem = "base64cost", costAmount = 10
    )

    /**
     * Sets up Bukkit mocks: getWorld("world"), block states for sign + container.
     * [contents] are placed in the container's inventory.
     * Returns the sign mock for verification.
     */
    private fun mockWorld(
        contents: Array<ItemStack?>,
        signX: Int = 100, signY: Int = 64, signZ: Int = 200,
        contX: Int = 50, contY: Int = 64, contZ: Int = 60
    ): Sign {
        mockkStatic(Bukkit::class)
        val world = mockk<World>(relaxed = true)
        every { world.isChunkLoaded(any(), any()) } returns true
        every { Bukkit.getWorld("world") } returns world

        // Sign
        val sign = mockk<Sign>(relaxed = true)
        val signBlock = mockk<Block>(relaxed = true)
        every { signBlock.state } returns sign
        every { world.getBlockAt(signX, signY, signZ) } returns signBlock

        // Container
        val containerInv = mockk<Inventory>(relaxed = true)
        every { containerInv.contents } returns contents
        val container = mockk<Container>(relaxed = true)
        every { container.inventory } returns containerInv
        val contLoc = mockk<Location>(relaxed = true)
        every { contLoc.world?.name } returns "world"
        every { contLoc.blockX } returns contX
        every { contLoc.blockY } returns contY
        every { contLoc.blockZ } returns contZ
        val containerBlock = mockk<Block>(relaxed = true)
        every { containerBlock.location } returns contLoc
        every { containerBlock.state } returns container
        every { world.getBlockAt(contX, contY, contZ) } returns containerBlock

        // PluginManager stub so container edit path doesn't NPE when firing events
        val pm = mockk<PluginManager>(relaxed = true)
        every { Bukkit.getPluginManager() } returns pm

        return sign
    }

    // ── Timer path tests ──────────────────────────────────────────────

    @Test
    fun `refreshAllSigns updates sign with stock count`() {
        val sellStack = mockk<ItemStack>(relaxed = true)
        mockkObject(ItemStackSerializer)
        every { ItemStackSerializer.deserialize("base64item") } returns sellStack

        val s = shop()
        val repo = mockk<ShopRepository>(relaxed = true)
        every { repo.all() } returns listOf(s)

        val contItem = mockk<ItemStack>(relaxed = true)
        every { contItem.isSimilar(sellStack) } returns true
        every { contItem.amount } returns 10

        val sign = mockWorld(contents = arrayOf(contItem))

        val listener = ContainerStockListener(repo, mockk(relaxed = true))
        listener.refreshAllSigns()

        verify { sign.line(3, any<Component>()) }
        verify { sign.update(true) }
        verify { repo.updateStock(s.id, 10) }
    }

    @Test
    fun `refreshAllSigns skips update when stock unchanged`() {
        val sellStack = mockk<ItemStack>(relaxed = true)
        mockkObject(ItemStackSerializer)
        every { ItemStackSerializer.deserialize("base64item") } returns sellStack

        val s = shop()
        val repo = mockk<ShopRepository>(relaxed = true)
        every { repo.all() } returns listOf(s)

        val contItem = mockk<ItemStack>(relaxed = true)
        every { contItem.isSimilar(sellStack) } returns true
        every { contItem.amount } returns 10

        val sign = mockWorld(contents = arrayOf(contItem))

        val listener = ContainerStockListener(repo, mockk(relaxed = true))

        // First call — should update
        listener.refreshAllSigns()
        verify(exactly = 1) { sign.line(3, any<Component>()) }
        verify(exactly = 1) { repo.updateStock(s.id, 10) }

        // Second call — stock unchanged, should skip
        listener.refreshAllSigns()
        verify(exactly = 1) { sign.line(3, any<Component>()) }
        verify(exactly = 1) { repo.updateStock(s.id, 10) }
    }

    @Test
    fun `refreshAllSigns skips unloaded chunk`() {
        val repo = mockk<ShopRepository>(relaxed = true)
        every { repo.all() } returns listOf(shop())

        mockkStatic(Bukkit::class)
        val world = mockk<World>(relaxed = true)
        every { world.isChunkLoaded(any(), any()) } returns false
        every { Bukkit.getWorld("world") } returns world

        val listener = ContainerStockListener(repo, mockk(relaxed = true))
        listener.refreshAllSigns()

        // Should never call getBlockAt — chunk not loaded
        verify(exactly = 0) { world.getBlockAt(any(), any(), any()) }
    }

    @Test
    fun `refreshAllSigns does nothing with empty shop list`() {
        val repo = mockk<ShopRepository>(relaxed = true)
        every { repo.all() } returns emptyList()

        val listener = ContainerStockListener(repo, mockk(relaxed = true))
        listener.refreshAllSigns()
        // Should not throw
    }

    // ── Trade path tests ──────────────────────────────────────────────

    @Test
    fun `onTransaction updates sign and persists stock`() {
        val sellStack = mockk<ItemStack>(relaxed = true)
        mockkObject(ItemStackSerializer)
        every { ItemStackSerializer.deserialize("base64item") } returns sellStack

        val s = shop()
        val repo = mockk<ShopRepository>(relaxed = true)
        every { repo.findById(1L) } returns s

        val contItem = mockk<ItemStack>(relaxed = true)
        every { contItem.isSimilar(sellStack) } returns true
        every { contItem.amount } returns 64

        val sign = mockWorld(contents = arrayOf(contItem))

        val event = PostShopTransactionEvent(
            mockk(relaxed = true),
            UUID.randomUUID(),
            contItem, 64, 100.0,
            shopId = 1L
        )
        val listener = ContainerStockListener(repo, mockk(relaxed = true))
        listener.onTransaction(event)

        verify { sign.line(3, any<Component>()) }
        verify { sign.update(true) }
        verify { repo.updateStock(1L, 64) }
    }

    @Test
    fun `onTransaction with unknown shopId does nothing`() {
        val repo = mockk<ShopRepository>(relaxed = true)
        every { repo.findById(999L) } returns null

        val event = PostShopTransactionEvent(
            mockk(relaxed = true),
            UUID.randomUUID(),
            mockk(relaxed = true), 1, 10.0,
            shopId = 999L
        )
        val listener = ContainerStockListener(repo, mockk(relaxed = true))
        listener.onTransaction(event)
        // Should not throw — returns early on null shop
    }

    // ── Depletion event tests ─────────────────────────────────────────

    @Test
    fun `zero stock fires ShopStockDepletedEvent via PluginManager`() {
        val sellStack = mockk<ItemStack>(relaxed = true)
        mockkObject(ItemStackSerializer)
        every { ItemStackSerializer.deserialize("base64item") } returns sellStack

        val ownerUuid = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
        val s = shop(owner = ownerUuid)
        val repo = mockk<ShopRepository>(relaxed = true)
        every { repo.all() } returns listOf(s)

        // Non-matching item → stock = 0
        val diffItem = mockk<ItemStack>(relaxed = true)
        every { diffItem.isSimilar(sellStack) } returns false

        val sign = mockWorld(contents = arrayOf(diffItem))

        // Capture the callEvent argument
        val pmSlot = slot<Event>()
        val pm = mockk<PluginManager>(relaxed = true)
        every { pm.callEvent(capture(pmSlot)) } answers { }
        every { Bukkit.getPluginManager() } returns pm

        val listener = ContainerStockListener(repo, mockk(relaxed = true))
        listener.refreshAllSigns()

        verify { sign.line(3, any<Component>()) }
        verify(exactly = 1) { pm.callEvent(any<ShopStockDepletedEvent>()) }
        val fired = pmSlot.captured as ShopStockDepletedEvent
        kotlin.test.assertEquals(ownerUuid, fired.ownerId)
    }

    @Test
    fun `depletion event not re-fired when still at zero`() {
        val sellStack = mockk<ItemStack>(relaxed = true)
        mockkObject(ItemStackSerializer)
        every { ItemStackSerializer.deserialize("base64item") } returns sellStack

        val s = shop()
        val repo = mockk<ShopRepository>(relaxed = true)
        every { repo.all() } returns listOf(s)

        val diffItem = mockk<ItemStack>(relaxed = true)
        every { diffItem.isSimilar(sellStack) } returns false

        val sign = mockWorld(contents = arrayOf(diffItem))

        val pm = mockk<PluginManager>(relaxed = true)
        every { Bukkit.getPluginManager() } returns pm

        val listener = ContainerStockListener(repo, mockk(relaxed = true))

        // First refresh at zero — should fire
        listener.refreshAllSigns()
        verify(exactly = 1) { pm.callEvent(any<ShopStockDepletedEvent>()) }

        // Second refresh still at zero — should NOT fire again
        listener.refreshAllSigns()
        verify(exactly = 1) { pm.callEvent(any<ShopStockDepletedEvent>()) }
    }
}

package net.badgersmc.em.infrastructure.listeners

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.em.domain.shop.Shop
import net.badgersmc.em.domain.shop.ShopRepository
import net.badgersmc.em.domain.shop.SignDirection
import net.kyori.adventure.text.Component
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.Sign
import org.bukkit.block.data.type.WallSign
import org.bukkit.block.sign.Side
import org.bukkit.block.sign.SignSide
import org.junit.jupiter.api.Test
import java.util.UUID

class DeletedShopSignListenerTest {
    private val shop = Shop(1, "stall69", UUID.randomUUID(), "world", 14, 100, 14,
        "world", 14, 100, 13, "item", 1, "currency", 1)
    private val shops = mockk<ShopRepository>()
    private val world = mockk<World>()
    private val listener = DeletedShopSignListener(shops)

    @Test
    fun `delete blanks only the front of the listing`() {
        val block = mockk<Block>()
        val sign = mockk<Sign>(relaxed = true)
        val front = mockk<SignSide>(relaxed = true)
        every { shops.findBySign("world", 14, 100, 14) } returns null
        every { world.getBlockAt(14, 100, 14) } returns block
        every { block.blockData } returns mockk<WallSign> { every { facing } returns BlockFace.SOUTH }
        every { block.state } returns sign
        every { sign.getSide(Side.FRONT) } returns front
        every { front.line(0) } returns Component.text("[SELL]")
        listener.clearListing(world, shop.copy(direction = SignDirection.SELL))
        for (i in 0..3) verify(exactly = 1) { front.line(i, Component.empty()) }
        verify(exactly = 1) { sign.update(false, false) }
        verify(exactly = 0) { sign.getSide(Side.BACK) }
    }

    @Test
    fun `replacement shop at the same sign survives delayed cleanup`() {
        every { shops.findBySign("world", 14, 100, 14) } returns shop.copy(id = 2)
        listener.clearListing(world, shop)
        verify(exactly = 0) { world.getBlockAt(any<Int>(), any<Int>(), any<Int>()) }
    }
}

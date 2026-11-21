package net.badgersmc.em.infrastructure.listeners

import io.mockk.*
import net.badgersmc.em.domain.ports.GuildProvider
import net.badgersmc.em.domain.ports.GuildProvider.GuildPermission
import net.badgersmc.em.domain.stall.*
import org.bukkit.Location
import org.bukkit.block.Container
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import java.util.UUID
import kotlin.test.*

class GuildStallProtectionTest {
    private val actor = UUID.randomUUID()
    private val guildId = UUID.randomUUID().toString()
    private val guilds = mockk<GuildProvider>()
    private val player = mockk<Player>()
    private val inventory = mockk<Inventory>()
    private val location = mockk<Location>()
    private val stall = Stall(StallId("s1"), "s1", "world", StallState.OWNED, OwnerRef.guild(guildId), null, 100, RentTerms.formula(1.0))
    private val listener = object : GuildStallProtectionListener(mockk(), guilds) {
        override fun at(location: Location) = listOf(stall)
    }

    @BeforeTest fun setup() {
        every { player.uniqueId } returns actor
        every { player.hasPermission(any<String>()) } returns false
        every { inventory.holder } returns mockk<Container>()
        every { inventory.location } returns location
        every { guilds.isMember(actor, guildId) } returns true
        every { guilds.hasShopPermission(actor, guildId, any()) } returns false
    }

    @Test fun `only a current stock editor can break a guild stall block`() {
        val event = mockk<org.bukkit.event.block.BlockBreakEvent>(relaxed = true)
        every { event.player } returns player
        every { event.block.location } returns location
        every { guilds.hasShopPermission(actor, guildId, GuildPermission.EDIT_SHOP_STOCK) } returns true
        listener.onBreak(event)
        verify(exactly = 0) { event.isCancelled = true }
        every { guilds.isMember(actor, guildId) } returns false
        listener.onBreak(event)
        verify(exactly = 1) { event.isCancelled = true }
    }

    @Test fun `membership does not grant chest viewing`() {
        val event = mockk<InventoryOpenEvent>(relaxed = true)
        every { event.player } returns player
        every { event.inventory } returns inventory
        listener.onOpen(event)
        verify { event.isCancelled = true }
    }

    @Test fun `view permission does not permit inventory changes`() {
        every { guilds.hasShopPermission(actor, guildId, GuildPermission.ACCESS_SHOP_CHESTS) } returns true
        val open = mockk<InventoryOpenEvent>(relaxed = true)
        every { open.player } returns player
        every { open.inventory } returns inventory
        listener.onOpen(open)
        verify(exactly = 0) { open.isCancelled = true }
        val click = mockk<InventoryClickEvent>(relaxed = true)
        every { click.whoClicked } returns player
        every { click.view.topInventory } returns inventory
        listener.onClick(click)
        verify { click.isCancelled = true }
    }

    @Test fun `departure revokes changes in an already open chest`() {
        every { guilds.hasShopPermission(actor, guildId, any()) } returns true
        every { guilds.isMember(actor, guildId) } returns false
        val click = mockk<InventoryClickEvent>(relaxed = true)
        every { click.whoClicked } returns player
        every { click.view.topInventory } returns inventory
        listener.onClick(click)
        verify { click.isCancelled = true }
    }

    @Test fun `ordinary menu inventory is outside container protection`() {
        every { inventory.holder } returns null
        every { inventory.location } returns null
        val click = mockk<InventoryClickEvent>(relaxed = true)
        every { click.whoClicked } returns player
        every { click.view.topInventory } returns inventory
        listener.onClick(click)
        verify(exactly = 0) { click.isCancelled = true }
    }

    @Test fun `block placement follows the current stock rank`() {
        val place = mockk<org.bukkit.event.block.BlockPlaceEvent>(relaxed = true)
        every { place.player } returns player
        every { place.block.location } returns location
        every { guilds.hasShopPermission(actor, guildId, GuildPermission.MODIFY_SHOP_PRICES) } returns true
        listener.onPlace(place)
        verify(exactly = 1) { place.isCancelled = true }
        clearMocks(place, answers = false)
        every { guilds.hasShopPermission(actor, guildId, GuildPermission.EDIT_SHOP_STOCK) } returns true
        listener.onPlace(place)
        verify(exactly = 0) { place.isCancelled = true }
    }

    @Test fun `stock drag into the guild chest checks rank again after demotion`() {
        val drag = mockk<org.bukkit.event.inventory.InventoryDragEvent>(relaxed = true)
        every { drag.whoClicked } returns player
        every { drag.view.topInventory } returns inventory
        every { inventory.size } returns 27
        every { drag.rawSlots } returns setOf(0, 27)
        every { guilds.hasShopPermission(actor, guildId, GuildPermission.EDIT_SHOP_STOCK) } returns true
        listener.onDrag(drag)
        verify(exactly = 0) { drag.isCancelled = true }
        every { guilds.hasShopPermission(actor, guildId, GuildPermission.EDIT_SHOP_STOCK) } returns false
        listener.onDrag(drag)
        verify(exactly = 1) { drag.isCancelled = true }
    }

    @Test fun `shop creation requires active guild membership and management permission`() {
        val creation = ShopCreateListener(mockk(), mockk(), mockk(), mockk(), mockk(), guilds)
        assertFalse(creation.canManageStall(stall, player))
        every { guilds.hasShopPermission(actor, guildId, GuildPermission.MANAGE_SHOPS) } returns true
        assertTrue(creation.canManageStall(stall, player))
        every { guilds.isMember(actor, guildId) } returns false
        assertFalse(creation.canManageStall(stall, player))
    }
}

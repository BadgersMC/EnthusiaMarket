package net.badgersmc.em.infrastructure.lumaguilds

import org.mockbukkit.mockbukkit.MockBukkit
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** REQ-400: the stall answer reaches LumaGuilds through its public API, loaded by name. */
class GuildShopLookupRegistrationTest {
    /** Same shape as `net.lumalyte.lg.api.GuildShopLookup`. */
    fun interface ShopLookup {
        fun ownsShop(guildId: UUID): Boolean
    }

    @AfterTest fun cleanup() { if (MockBukkit.isMocked()) MockBukkit.unmock() }

    @Test fun `registers a provider that answers from the stall count`() {
        val server = MockBukkit.mock()
        val plugin = MockBukkit.createMockPlugin()
        val owner = UUID.randomUUID()
        assertTrue(GuildShopLookupRegistration.register(plugin, ShopLookup::class.java) { it == owner })
        val provider = assertNotNull(server.servicesManager.load(ShopLookup::class.java))
        assertTrue(provider.ownsShop(owner))
        assertFalse(provider.ownsShop(UUID.randomUUID()))
        assertEquals(provider, provider, "equals works on the proxy")
        provider.hashCode()
        provider.toString()
    }

    @Test fun `a lookup failure answers no instead of breaking the guild page`() {
        val server = MockBukkit.mock()
        val plugin = MockBukkit.createMockPlugin()
        GuildShopLookupRegistration.register(plugin, ShopLookup::class.java) { error("db down") }
        assertFalse(server.servicesManager.load(ShopLookup::class.java)!!.ownsShop(UUID.randomUUID()))
    }

    @Test fun `no LumaGuilds API means nothing is registered`() {
        MockBukkit.mock()
        val plugin = MockBukkit.createMockPlugin()
        assertFalse(GuildShopLookupRegistration.register(plugin, null) { true })
    }
}

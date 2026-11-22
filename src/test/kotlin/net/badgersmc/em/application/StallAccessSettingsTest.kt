package net.badgersmc.em.application

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.mockk.every
import io.mockk.mockk
import net.badgersmc.em.domain.ports.GuildProvider
import net.badgersmc.em.domain.ports.MarketMutationGate
import net.badgersmc.em.domain.stall.*
import net.badgersmc.em.infrastructure.persistence.StallAccessSettingsSql
import net.badgersmc.em.infrastructure.persistence.StallRepositorySql
import net.badgersmc.nexus.persistence.MigrationRunner
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class StallAccessSettingsTest {
    private lateinit var ds: HikariDataSource
    private lateinit var stalls: StallAccessIndex
    private lateinit var repo: StallAccessSettingsSql
    private lateinit var service: StallAccessSettingsService
    private val owner = UUID.randomUUID()
    private val visitor = UUID.randomUUID()
    private val guild = UUID.randomUUID().toString()
    private val ally = UUID.randomUUID().toString()
    private val guilds = mockk<GuildProvider>(relaxed = true)
    private val gate = mockk<MarketMutationGate>(relaxed = true)
    private fun stall() = Stall(StallId("s1"), "s1", "world", StallState.OWNED,
        OwnerRef.guild(guild), Instant.parse("2026-10-08T00:00:00Z"), 100, RentTerms.formula(1.0))

    @BeforeTest fun setup() {
        ds = HikariDataSource(HikariConfig().apply { jdbcUrl = "jdbc:sqlite::memory:"; maximumPoolSize = 1 })
        MigrationRunner(ds, "migrations", javaClass.classLoader).runAll()
        stalls = StallAccessIndex(StallRepositorySql(ds))
        stalls.create(stall())
        repo = StallAccessSettingsSql(ds)
        service = StallAccessSettingsService(stalls, repo, guilds, gate)
        every { guilds.isMember(owner, guild) } returns true
        every { guilds.hasShopPermission(owner, guild, GuildProvider.GuildPermission.MANAGE_SHOPS) } returns true
        every { guilds.isMember(visitor, ally) } returns true
        every { guilds.areAllied(guild, ally) } returns true
    }
    @AfterTest fun cleanup() = ds.close()

    @Test fun `allies are off by default and selected capabilities revoke with alliance`() {
        assertFalse(service.alliedAllows("s1", visitor, StallCapability.TRADE))
        service.edit(owner, "s1") { it.copy(visitorFlags = mapOf(StallCapability.TRADE to false),
            allies = mapOf(ally to setOf(StallCapability.TRADE, StallCapability.CHESTS))) }
        assertTrue(service.allows("s1", visitor, StallCapability.TRADE))
        assertTrue(service.allows("s1", visitor, StallCapability.CHESTS))
        assertFalse(service.allows("s1", visitor, StallCapability.STOCK))
        assertFalse(service.mayManage(stall(), visitor))
        every { guilds.areAllied(guild, ally) } returns false
        assertFalse(service.allows("s1", visitor, StallCapability.TRADE))
        assertFalse(service.allows("s1", visitor, StallCapability.CHESTS))
        // Inactive configured grant can be removed without preventing unrelated flag edits.
        service.edit(owner, "s1") { it.copy(allies = emptyMap()) }
    }

    @Test fun `blacklist overrides every allied grant and managers cannot be blacklisted`() {
        service.edit(owner, "s1") { it.copy(blacklist = setOf(visitor), allies = mapOf(ally to StallCapability.entries.toSet())) }
        StallCapability.entries.forEach { assertFalse(service.allows("s1", visitor, it)) }
        assertFailsWith<IllegalStateException> { service.edit(owner, "s1") { it.copy(blacklist = setOf(owner)) } }
        assertEquals(setOf(visitor), repo.all().single().blacklist)
    }

    @Test fun `management permission alone cannot replace field specific guild permissions`() {
        assertTrue(service.mayManage(stall(), owner))
        assertFalse(service.allows("s1", owner, StallCapability.PRICES))
        every { guilds.hasShopPermission(owner, guild, GuildProvider.GuildPermission.MODIFY_SHOP_PRICES) } returns true
        assertTrue(service.allows("s1", owner, StallCapability.PRICES))
    }

    @Test fun `settings round trip and reject stale revisions and ownership without overwriting`() {
        val old = service.current(stall())
        val saved = service.edit(owner, "s1") { it.copy(blockedEffects = setOf("INVISIBILITY", "POISON"), allowIncomingPotions = true) }
        assertEquals(saved, repo.all().single())
        assertFailsWith<IllegalStateException> { repo.save(old, stall()) }
        val successor = stall().copy(owner = OwnerRef.solo(visitor), ownerSince = Instant.now())
        stalls.save(successor)
        assertEquals(StallAccessSettings.defaults(successor), service.current(successor))
        assertFailsWith<IllegalStateException> { repo.save(saved, stall()) }
        assertTrue(repo.all().isEmpty())
    }

    @Test fun `forfeiture clears only the lost tenancy including on reacquisition`() {
        val original = stall()
        val other = original.copy(id = StallId("s2"), regionId = "s2", owner = OwnerRef.solo(owner))
        stalls.create(other)
        for (id in listOf("s1", "s2")) service.edit(owner, id) { it.copy(blacklist = setOf(visitor)) }
        stalls.save(original.copy(state = StallState.GRACE))
        assertTrue(service.blacklistDenies("s1", visitor))
        stalls.save(original.copy(state = StallState.EMERGENCY_AUCTIONING))
        stalls.save(original)
        assertFalse(service.blacklistDenies("s1", visitor))
        assertTrue(service.blacklistDenies("s2", visitor))
        assertEquals("s2", repo.all().single().stallId)
    }

    @Test fun `refunded sellback expires policy while rollback preserves it`() {
        val original = stall()
        val released = original.copy(state = StallState.UNOWNED)
        service.edit(owner, "s1") { it.copy(blacklist = setOf(visitor)) }
        stalls.savePendingSellback(released)
        assertFalse(service.blacklistDenies("s1", visitor))
        stalls.save(original)
        assertTrue(service.blacklistDenies("s1", visitor))
        stalls.savePendingSellback(released)
        stalls.finishSellbackAccess(original)
        stalls.save(original)
        assertFalse(service.blacklistDenies("s1", visitor))
        assertTrue(repo.all().isEmpty())
    }

    @Test fun `startup removes legacy blacklist on an unowned stall`() {
        service.edit(owner, "s1") { it.copy(blacklist = setOf(visitor)) }
        repo.pruneEndedTenancies(mapOf("s1" to stall().copy(state = StallState.MODERATION_HOLD)))
        assertEquals(setOf(visitor), repo.all().single().blacklist)
        stalls.savePendingSellback(stall().copy(state = StallState.UNOWNED))
        repo.pruneEndedTenancies(stalls.currentStalls().associateBy { it.id.value })
        assertTrue(repo.all().isEmpty())
    }

    @Test fun `moderation reservations and stale caches deny edits and ally privilege`() {
        service.edit(owner, "s1") { it.copy(allies = mapOf(ally to setOf(StallCapability.CHESTS))) }
        stalls.invalidate("s1")
        assertFalse(service.mayManage(stall(), owner))
        assertFalse(service.alliedAllows("s1", visitor, StallCapability.CHESTS))
        stalls.refresh("s1")
        every { gate.isStallLocked("s1") } returns true
        assertFailsWith<IllegalStateException> { service.edit(owner, "s1") { it.copy(blacklist = setOf(visitor)) } }
        assertFalse(service.alliedAllows("s1", visitor, StallCapability.CHESTS))
    }

    @Test fun `failed insert rolls back previous settings deletion`() {
        val saved = service.edit(owner, "s1") { it.copy(blacklist = setOf(visitor)) }
        ds.connection.use { connection -> connection.createStatement().use {
            it.execute("CREATE TRIGGER reject_policy BEFORE INSERT ON stall_access_settings WHEN NEW.allow_potions = 1 BEGIN SELECT RAISE(ABORT, 'synthetic write failure'); END")
        } }
        assertFails { repo.save(saved.copy(allowIncomingPotions = true), stall()) }
        assertEquals(saved, repo.all().single())
    }

    @Test fun `uncertain committed write remains fail closed through ordinary saves until durable reconciliation`() {
        val failing = mockk<StallAccessSettingsRepository>()
        var readsFail = false
        every { failing.all() } answers { if (readsFail) error("Read unavailable") else repo.all() }
        failCommitAcknowledgement(failing) { readsFail = true }
        val guarded = StallAccessSettingsService(stalls, failing, guilds, gate)
        assertFails { guarded.edit(owner, "s1") { it.copy(blacklist = setOf(visitor)) } }
        stalls.save(stall())
        assertUncertainDenial(guarded)
        readsFail = false
        guarded.refreshIfUncertain("s1")
        assertTrue(guarded.mayManage(stall(), owner))
        assertFalse(guarded.allows("s1", visitor, StallCapability.ITEM_PICKUP))
        assertEquals(setOf(visitor), guarded.current(stall()).blacklist)
    }

    private fun failCommitAcknowledgement(failing: StallAccessSettingsRepository, failReads: () -> Unit) {
        every { failing.save(any(), any()) } answers {
            repo.save(firstArg(), secondArg())
            failReads()
            error("Commit acknowledgement unavailable")
        }
    }

    private fun assertUncertainDenial(guarded: StallAccessSettingsService) {
        assertFalse(guarded.allows("s1", owner, StallCapability.ENTRY))
        assertFalse(guarded.mayManage(stall(), owner))
    }
}

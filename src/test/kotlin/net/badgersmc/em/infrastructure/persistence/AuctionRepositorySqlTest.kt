package net.badgersmc.em.infrastructure.persistence

import net.badgersmc.em.domain.stall.RentPayment
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import net.badgersmc.em.domain.auction.Auction
import net.badgersmc.em.domain.auction.AuctionId
import net.badgersmc.em.domain.auction.AuctionState
import net.badgersmc.em.domain.auction.Bid
import net.badgersmc.em.domain.stall.OwnerRef
import net.badgersmc.em.domain.stall.RentTerms
import net.badgersmc.em.domain.stall.Stall
import net.badgersmc.em.domain.stall.StallId
import net.badgersmc.em.domain.stall.StallState
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AuctionRepositorySqlTest {
    @Test fun `emergency listings survive shop cleanup and respect discovery settings and auction identity`() {
        val auction = sampleAuction()
        ds.connection.use { connection ->
            connection.createStatement().use { statement ->
                for (id in 1..4) {
                    statement.executeUpdate("""INSERT INTO shop_items
                        (id, stall_id, owner, sign_world, sign_x, sign_y, sign_z,
                         container_world, container_x, container_y, container_z, sell_item, cost_item,
                         sell_material, sell_amount, cost_amount, search_enabled, admin_shop)
                        VALUES ($id,'stall_01','owner','world',$id,0,0,'world',$id,0,1,'','','REDSTONE',8,$id,
                            ${if (id == 3) 0 else 1},${if (id == 4) 1 else 0})""")
                }
            }
        }
        repo.createEmergency(auction)
        ds.connection.use { it.createStatement().use { statement -> statement.executeUpdate("DELETE FROM shop_items") } }
        val listings = AuctionRepositorySql(ds).previewListings(auction.id, 0, 6)
        assertEquals(listOf(1, 2), listings.map { it.amount })
        assertTrue(listings.all { it.quantity == 8 && it.item == "redstone" && it.payment == "raw gold" })
        assertEquals(listings.drop(1), repo.previewListings(auction.id, 1, 5))
        assertTrue(repo.previewListings(AuctionId("different"), 0, 6).isEmpty())
    }

    @Test fun `emergency capture failure rolls back the new auction`() {
        ds.connection.use { it.createStatement().use { statement -> statement.executeUpdate("DROP TABLE shop_items") } }
        val auction = sampleAuction()
        assertFailsWith<java.sql.SQLException> { repo.createEmergency(auction) }
        assertNull(repo.findById(auction.id))
    }

    @Test fun `50 imported lots commit together and a late conflict rolls everything back`() {
        val stalls = StallRepositorySql(ds)
        val lots = (1..50).map { number ->
            val id = "rehearsal_" + number.toString().padStart(2, '0')
            val stall = Stall(StallId(id), id, "world", StallState.UNOWNED,
                OwnerRef.unowned(), null, 0, RentTerms.formula(1.0))
            stalls.create(stall)
            stall to sampleAuction(id, id, startingBid = 10L + number)
                .copy(openingRoundId = "rehearsal-opening")
        }
        val unrelated = lots.first().first.copy(id = StallId("unrelated"), regionId = "unrelated")
        stalls.create(unrelated)
        val (last, lastAuction) = lots.last()
        stalls.save(last.copy(state = StallState.AUCTIONING))
        assertEquals(last.id, repo.openImportedLots(lots))
        assertTrue(lots.all { repo.findByStall(it.first.id).isEmpty() })
        assertEquals(StallState.UNOWNED, stalls.findById(lots.first().first.id)?.state)
        stalls.save(last)

        assertNull(repo.openImportedLots(lots))
        for ((stall, auction) in lots) {
            assertEquals(auction, repo.findById(auction.id))
            assertEquals(StallState.AUCTIONING, stalls.findById(stall.id)?.state)
        }
        assertEquals(lastAuction, repo.findById(lastAuction.id))
        assertEquals(StallState.UNOWNED, stalls.findById(unrelated.id)?.state)
        assertTrue(repo.findByStall(unrelated.id).isEmpty())
        assertEquals(lots.first().first.id, repo.openImportedLots(lots))
    }

    @Test fun `guild bidder funding survives create and reload`() {
        val guildId = UUID.randomUUID().toString()
        val auction = sampleAuction(highBid = Bid(UUID.randomUUID(), 100, Instant.now(), guildId))
        repo.create(auction)
        assertEquals(guildId, repo.findById(auction.id)?.highBid?.guildId)
        repo.save(auction.copy(highBid = auction.highBid!!.copy(amount = 120)))
        assertEquals(guildId, repo.findById(auction.id)?.highBid?.guildId)
    }
    @Test fun `guild lead remains reserved until the auction stops being open`() {
        val guild = UUID.randomUUID().toString()
        val auction = sampleAuction(highBid = Bid(UUID.randomUUID(), 100, Instant.now(), guild))
            .copy(openingRoundId = "new-market")
        repo.create(auction)
        val reloaded = AuctionRepositorySql(ds)
        val other = AuctionId("other")
        assertEquals("new-market", reloaded.findById(auction.id)?.openingRoundId)
        assertTrue(reloaded.hasOtherOpenGuildBid(guild, other))
        assertFalse(reloaded.hasOtherOpenGuildBid(guild, auction.id))

        repo.save(auction.copy(highBid = Bid(UUID.randomUUID(), 101, Instant.now())))
        assertFalse(reloaded.hasOtherOpenGuildBid(guild, other))
    }

    @Test fun `opening round award rolls back with the stall and persists after transfer`() {
        val guild = UUID.randomUUID().toString()
        val owner = OwnerRef.guild(guild)
        val stalls = StallRepositorySql(ds)
        val award = Stall(StallId("stall_01"), "region_01", "world", StallState.OWNED,
            owner, Instant.now(), 100, RentTerms.formula(1.0))
        val secondAward = award.copy(id = StallId("stall_02"), regionId = "region_02")

        assertFailsWith<MarketModerationConflictException> { stalls.saveOpeningRoundGuildAward(award, "new-market") }
        assertFalse(repo.hasGuildOpeningRoundWin("new-market", guild))

        stalls.create(award.releaseOwnership())
        stalls.create(secondAward.releaseOwnership())
        stalls.saveOpeningRoundGuildAward(award, "new-market")
        stalls.save(award.releaseOwnership())

        val reloaded = AuctionRepositorySql(ds)
        assertTrue(reloaded.hasGuildOpeningRoundWin("new-market", guild))
        assertFalse(reloaded.hasGuildOpeningRoundWin("another-round", guild))
        assertFailsWith<java.sql.SQLException> { stalls.saveOpeningRoundGuildAward(secondAward, "new-market") }
        assertEquals(StallState.UNOWNED, stalls.findById(secondAward.id)?.state)
    }

    @Test fun `cancellation and payment claim survive restart without a second credit`() {
        val bid = Bid(UUID.randomUUID(), 125, Instant.now())
        val auction = sampleAuction(highBid = bid)
        repo.create(auction)
        val payment = net.badgersmc.em.domain.auction.AuctionPayment(auction.id, "REFUND", OwnerRef.solo(bid.bidder), bid.amount)
        repo.commitTransition(auction, auction.close(), null, payment)
        val restarted = AuctionRepositorySql(ds)
        assertFailsWith<IllegalStateException> { restarted.commitTransition(auction, auction.close(), null, payment.copy(id = "duplicate")) }
        assertEquals(listOf(payment), restarted.pendingPayments(0))
        assertTrue(restarted.claimPayment(payment.id))
        assertFalse(restarted.claimPayment(payment.id))
        assertTrue(restarted.pendingPayments(Long.MAX_VALUE).isEmpty())
        restarted.finishPayment(payment.id, false, 60)
        assertTrue(restarted.pendingPayments(59).isEmpty())
        assertEquals(listOf(payment), restarted.pendingPayments(60))
        assertTrue(restarted.claimPayment(payment.id))
        restarted.finishPayment(payment.id, true, 0)
        assertTrue(restarted.pendingPayments(Long.MAX_VALUE).isEmpty())
    }

    @Test fun `failed award rolls back auction and payout then commits both on retry`() {
        val bid = Bid(UUID.randomUUID(), 150, Instant.now())
        val auction = sampleAuction(highBid = bid)
        repo.create(auction)
        val stall = Stall(auction.stallId, "region", "world", StallState.OWNED, OwnerRef.solo(bid.bidder),
            Instant.now(), bid.amount, RentTerms.formula(1.0))
        val payment = net.badgersmc.em.domain.auction.AuctionPayment(auction.id, "SELLER", OwnerRef.solo(UUID.randomUUID()), 140)
        assertFailsWith<MarketModerationConflictException> { repo.commitTransition(auction, auction.close(), stall, payment) }
        assertEquals(AuctionState.OPEN, repo.findById(auction.id)?.state)
        assertTrue(repo.pendingPayments(0).isEmpty())
        val stalls = StallRepositorySql(ds)
        stalls.create(stall.releaseOwnership())
        repo.commitTransition(auction, auction.close(), stall, payment)
        assertEquals(stall.owner, stalls.findById(stall.id)?.owner)
        assertEquals(AuctionState.CLOSED, repo.findById(auction.id)?.state)
        assertEquals(listOf(payment), repo.pendingPayments(0))
    }

    @Test fun `paid rent receipt and new expiry commit together`() {
        val paidAt = Instant.ofEpochMilli(3000)
        val payer = OwnerRef.guild("guild-rent")
        val stall = Stall(StallId("rent"), "region", "world", StallState.OWNED, payer, paidAt,
            400, RentTerms.formula(1.0), nextRentAt = paidAt.plusSeconds(90))
        val stalls = StallRepositorySql(ds)
        stalls.create(stall)
        val extended = stall.copy(nextRentAt = stall.nextRentAt!!.plusSeconds(3600))
        stalls.savePaidExtension(extended, RentPayment(payer, 77, paidAt, stall.nextRentAt!!))
        ds.connection.use { conn -> conn.createStatement().use { statement ->
            statement.executeQuery("SELECT * FROM rent_ledger").use { row ->
                assertTrue(row.next())
                assertEquals(payer.id, row.getString("payer_id"))
                assertEquals("GUILD", row.getString("payer_type"))
                assertEquals(77, row.getInt("amount"))
                assertEquals(stall.nextRentAt!!.toEpochMilli(), row.getLong("covered_from"))
                assertEquals(extended.nextRentAt!!.toEpochMilli(), row.getLong("covered_until"))
                assertFalse(row.next())
            }
            statement.execute("CREATE TRIGGER reject_receipt BEFORE INSERT ON rent_ledger BEGIN SELECT RAISE(ABORT,'unavailable'); END")
        } }
        assertFailsWith<java.sql.SQLException> { stalls.savePaidExtension(stall, RentPayment(payer, 77, paidAt, paidAt)) }
        assertEquals(extended.nextRentAt, stalls.findById(stall.id)?.nextRentAt)
    }

    private lateinit var ds: HikariDataSource
    private lateinit var repo: AuctionRepositorySql

    @BeforeTest fun setUp() {
        ds = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:sqlite::memory:"
            maximumPoolSize = 1
        })
        net.badgersmc.nexus.persistence.MigrationRunner(ds, "migrations", this::class.java.classLoader).runAll()
        repo = AuctionRepositorySql(ds)
    }

    @AfterTest fun tearDown() { ds.close() }

    @Test fun `findById returns null for unknown id`() {
        assertNull(repo.findById(AuctionId("nope")))
    }

    @Test fun `create then findById round-trips`() {
        val a = sampleAuction()
        repo.create(a)
        val found = repo.findById(AuctionId("auc-0001"))
        assertNotNull(found)
        assertEquals(a.id, found.id)
        assertEquals(a.stallId, found.stallId)
        assertEquals(a.state, found.state)
        assertEquals(a.startAt, found.startAt)
        assertEquals(a.endAt, found.endAt)
        assertEquals(a.startingBid, found.startingBid)
        assertEquals(a.highBid, found.highBid)
        assertEquals(a.antiSnipeWindow, found.antiSnipeWindow)
        assertEquals(a.antiSnipeExtension, found.antiSnipeExtension)
    }

    @Test fun `create then findById with a high bid`() {
        val bidder = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val bidAt = Instant.parse("2026-01-01T12:00:00Z")
        val bid = Bid(bidder, 500L, bidAt)
        val a = sampleAuction(highBid = bid)
        repo.create(a)
        val found = repo.findById(AuctionId("auc-0001"))
        assertNotNull(found)
        assertEquals(bid, found.highBid)
        assertEquals(500L, found.highBid!!.amount)
        assertEquals(bidder, found.highBid!!.bidder)
    }

    @Test fun `allOpen returns only open auctions whose end time hasn't passed`() {
        val future = Instant.parse("2030-01-01T00:00:00Z")
        val past = Instant.parse("2020-01-01T00:00:00Z")
        repo.create(sampleAuction(id = "auc-001", endAt = future))        // open + future
        repo.create(sampleAuction(id = "auc-002", endAt = past))          // open + expired
        repo.create(sampleAuction(id = "auc-003", state = AuctionState.CLOSED, endAt = future)) // closed
        repo.create(sampleAuction(id = "auc-004", state = AuctionState.CANCELLED, endAt = future)) // cancelled

        val open = repo.allOpen()
        assertEquals(1, open.size)
        assertEquals("auc-001", open[0].id.value)
    }

    @Test fun `findOpenByStall returns open auction for a given stall`() {
        val future = Instant.parse("2030-01-01T00:00:00Z")
        repo.create(sampleAuction(id = "auc-001", stallId = "stall_01", endAt = future))
        repo.create(sampleAuction(id = "auc-002", stallId = "stall_01", endAt = future, state = AuctionState.CLOSED))

        val found = repo.findOpenByStall(StallId("stall_01"))
        assertNotNull(found)
        assertEquals("auc-001", found.id.value)
    }

    @Test fun `findOpenByStall returns null if no open auction exists`() {
        assertNull(repo.findOpenByStall(StallId("stall_01")))
    }

    @Test fun `save updates auction fields`() {
        val a = sampleAuction(endAt = Instant.parse("2030-01-01T00:00:00Z"))
        repo.create(a)

        val bidder = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val bidAt = Instant.parse("2030-01-01T06:00:00Z")
        val updated = a.placeBid(bidder, 200L, bidAt)
        repo.save(updated)

        val found = repo.findById(AuctionId("auc-0001"))!!
        assertEquals(200L, found.highBid!!.amount)
        assertEquals(bidder, found.highBid!!.bidder)
    }

    @Test fun `save changes status to CLOSED`() {
        val a = sampleAuction(endAt = Instant.parse("2030-01-01T00:00:00Z"))
        repo.create(a)

        val closed = a.close()
        repo.save(closed)

        val found = repo.findById(AuctionId("auc-0001"))!!
        assertEquals(AuctionState.CLOSED, found.state)
    }

    @Test fun `findExpired returns open auctions past their end time`() {
        val past = Instant.parse("2020-01-01T00:00:00Z")
        val future = Instant.parse("2030-01-01T00:00:00Z")
        repo.create(sampleAuction(id = "auc-001", endAt = past))           // expired open
        repo.create(sampleAuction(id = "auc-002", endAt = future))         // not expired
        repo.create(sampleAuction(id = "auc-003", state = AuctionState.CLOSED, endAt = past)) // closed

        val expired = repo.findExpired()
        assertEquals(1, expired.size)
        assertEquals("auc-001", expired[0].id.value)
    }

    @Test fun `delete removes auction`() {
        repo.create(sampleAuction())
        repo.delete(AuctionId("auc-0001"))
        assertNull(repo.findById(AuctionId("auc-0001")))
    }

    @Test fun `findByStall returns all auctions for a stall regardless of state`() {
        val future = Instant.parse("2030-01-01T00:00:00Z")
        repo.create(sampleAuction(id = "auc-001", stallId = "stall_01", endAt = future))
        repo.create(sampleAuction(id = "auc-002", stallId = "stall_01", state = AuctionState.CLOSED, endAt = future))
        repo.create(sampleAuction(id = "auc-003", stallId = "stall_01", state = AuctionState.CANCELLED, endAt = future))
        repo.create(sampleAuction(id = "auc-004", stallId = "stall_02", endAt = future)) // different stall

        val results = repo.findByStall(StallId("stall_01"))
        assertEquals(3, results.size)
        assertEquals(setOf("auc-001", "auc-002", "auc-003"), results.map { it.id.value }.toSet())
    }

    @Test fun `findByStall returns empty for unknown stall`() {
        assertEquals(0, repo.findByStall(StallId("nope")).size)
    }
}

private fun sampleAuction(
    id: String = "auc-0001",
    stallId: String = "stall_01",
    startingBid: Long = 100L,
    state: AuctionState = AuctionState.OPEN,
    highBid: Bid? = null,
    endAt: Instant = Instant.parse("2026-01-02T00:00:00Z"),
) = Auction(
    id = AuctionId(id),
    stallId = StallId(stallId),
    state = state,
    startAt = Instant.parse("2026-01-01T00:00:00Z"),
    endAt = endAt,
    startingBid = startingBid,
    highBid = highBid,
    antiSnipeWindow = Duration.ofMinutes(1),
    antiSnipeExtension = Duration.ofMinutes(1)
)

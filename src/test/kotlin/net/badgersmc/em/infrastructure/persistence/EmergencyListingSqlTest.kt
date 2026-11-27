package net.badgersmc.em.infrastructure.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import net.badgersmc.em.domain.auction.Auction
import net.badgersmc.em.domain.auction.AuctionId
import net.badgersmc.em.domain.auction.AuctionState
import net.badgersmc.em.domain.stall.StallId
import net.badgersmc.nexus.persistence.MigrationRunner
import java.time.Duration
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EmergencyListingSqlTest {
    private lateinit var dataSource: HikariDataSource
    private lateinit var repository: AuctionRepositorySql

    @BeforeTest
    fun setUp() {
        dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:sqlite::memory:"
            maximumPoolSize = 1
        })
        MigrationRunner(dataSource, "migrations", this::class.java.classLoader).runAll()
        repository = AuctionRepositorySql(dataSource)
    }

    @AfterTest
    fun tearDown() = dataSource.close()

    @Test
    fun `emergency listings survive shop cleanup and respect discovery settings and auction identity`() {
        val auction = sampleAuction()
        insertShops()
        repository.create(auction, captureEmergencyListings = true)
        dataSource.connection.use { it.createStatement().use { statement -> statement.executeUpdate("DELETE FROM shop_items") } }

        val listings = repository.previewListings(auction.id, 0, 6)
        assertEquals(listOf(1, 2), listings.map { it.amount })
        assertTrue(listings.all { it.quantity == 8 && it.item == "redstone" && it.payment == "raw gold" })
        assertEquals(listings.drop(1), repository.previewListings(auction.id, 1, 5))
        assertTrue(repository.previewListings(AuctionId("different"), 0, 6).isEmpty())
    }

    @Test
    fun `emergency capture failure rolls back the new auction`() {
        dataSource.connection.use { it.createStatement().use { statement -> statement.executeUpdate("DROP TABLE shop_items") } }
        val auction = sampleAuction()
        assertFailsWith<java.sql.SQLException> { repository.create(auction, captureEmergencyListings = true) }
        assertNull(repository.findById(auction.id))
    }

    private fun insertShops() {
        dataSource.connection.use { connection -> connection.createStatement().use { statement ->
            for (id in 1..4) statement.executeUpdate(shopInsert(id))
        } }
    }

    private fun shopInsert(id: Int): String = """INSERT INTO shop_items
        (id, stall_id, owner, sign_world, sign_x, sign_y, sign_z,
         container_world, container_x, container_y, container_z, sell_item, cost_item,
         sell_material, sell_amount, cost_amount, search_enabled, admin_shop)
        VALUES ($id,'stall_01','owner','world',$id,0,0,'world',$id,0,1,'','','REDSTONE',8,$id,
            ${if (id == 3) 0 else 1},${if (id == 4) 1 else 0})"""

    private fun sampleAuction() = Auction(
        id = AuctionId("auc-emergency"),
        stallId = StallId("stall_01"),
        state = AuctionState.OPEN,
        startAt = Instant.parse("2026-01-01T00:00:00Z"),
        endAt = Instant.parse("2030-01-01T00:00:00Z"),
        startingBid = 10,
        highBid = null,
        antiSnipeWindow = Duration.ofSeconds(30),
        antiSnipeExtension = Duration.ofSeconds(60),
        auctionDuration = Duration.ofHours(24),
    )
}

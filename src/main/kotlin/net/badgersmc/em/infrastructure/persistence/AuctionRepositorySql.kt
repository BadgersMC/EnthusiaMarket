package net.badgersmc.em.infrastructure.persistence

import net.badgersmc.em.domain.auction.Auction
import net.badgersmc.em.domain.auction.AuctionId
import net.badgersmc.em.domain.auction.AuctionRepository
import net.badgersmc.em.domain.auction.AuctionPaymentRepository
import net.badgersmc.em.domain.auction.AuctionState
import net.badgersmc.em.domain.auction.Bid
import net.badgersmc.em.domain.stall.StallId
import net.badgersmc.em.domain.stall.Stall
import net.badgersmc.nexus.annotations.Repository
import net.badgersmc.em.domain.auction.AuctionPayment
import net.badgersmc.em.domain.stall.OwnerRef
import net.badgersmc.em.domain.stall.OwnerType
import net.badgersmc.em.infrastructure.moderation.inTransaction
import java.sql.PreparedStatement
import java.sql.Connection
import java.sql.ResultSet
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

@Repository
class AuctionRepositorySql(private val ds: DataSource) : AuctionRepository, AuctionPaymentRepository by AuctionPaymentSql(ds) {

    override fun findById(id: AuctionId): Auction? = queryOne("SELECT * FROM auctions WHERE id = ?") {
        setString(1, id.value)
    }

    override fun findOpenByStall(stallId: StallId): Auction? =
        queryOne("SELECT * FROM auctions WHERE stall_id = ? AND state = 'OPEN' AND end_at > ? ORDER BY start_at DESC LIMIT 1") {
            setString(1, stallId.value)
            setLong(2, Instant.now().toEpochMilli())
        }

    override fun allOpen(): List<Auction> =
        queryMany("SELECT * FROM auctions WHERE state = 'OPEN' AND end_at > ?") {
            setLong(1, Instant.now().toEpochMilli())
        }

    override fun hasOtherOpenGuildBid(guildId: String, auctionId: AuctionId): Boolean =
        exists("SELECT 1 FROM auctions WHERE state = 'OPEN' AND high_bid_guild_id = ? AND id <> ?") {
            setString(1, guildId)
            setString(2, auctionId.value)
        }

    override fun hasGuildOpeningRoundWin(roundId: String, guildId: String): Boolean =
        exists("SELECT 1 FROM guild_auction_round_wins WHERE round_id = ? AND guild_id = ?") {
            setString(1, roundId)
            setString(2, guildId)
        }

    override fun createEmergency(auction: Auction) {
        ds.inTransaction { connection ->
            insert(connection, auction)
            EmergencyListingSql.capture(connection, auction)
        }
    }

    override fun previewListings(id: AuctionId, offset: Long, limit: Int) =
        ds.connection.use { EmergencyListingSql.page(it, id, offset, limit) }

    override fun create(auction: Auction) {
        ds.connection.use { conn ->
            insert(conn, auction)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun openImportedLots(lots: List<Pair<Stall, Auction>>): StallId? =
        ds.connection.use { conn ->
            conn.autoCommit = false
            try {
                val conflict = stageImportedLots(conn, lots)
                if (conflict == null) conn.commit() else conn.rollback()
                conflict
            } catch (failure: Exception) {
                try {
                    conn.rollback()
                } catch (rollbackFailure: Exception) {
                    failure.addSuppressed(rollbackFailure)
                }
                throw failure
            }
        }

    private fun stageImportedLots(conn: Connection, lots: List<Pair<Stall, Auction>>): StallId? {
        conn.prepareStatement(
            """UPDATE stalls SET state = 'AUCTIONING'
               WHERE id = ? AND state = 'UNOWNED' AND owner_type = 'NONE'
                 AND world = ? AND region_id = ? AND moderation_revision = ?
                 AND NOT EXISTS (SELECT 1 FROM market_moderation_locks WHERE stall_id = ?)
                 AND NOT EXISTS (SELECT 1 FROM sell_offers WHERE stall_id = ?)
                 AND NOT EXISTS (SELECT 1 FROM auctions WHERE stall_id = ? AND state = 'OPEN')"""
        ).use { update ->
            for ((stall, auction) in lots.sortedBy { it.first.id.value }) {
                bindImportedStall(update, stall)
                if (update.executeUpdate() != 1) return stall.id
                insert(conn, auction)
            }
        }
        return null
    }

    private fun bindImportedStall(update: PreparedStatement, stall: Stall) {
        val id = stall.id.value
        update.setString(1, id)
        update.setString(2, stall.world)
        update.setString(3, stall.regionId)
        update.setLong(4, stall.moderationRevision)
        update.setString(5, id)
        update.setString(6, id)
        update.setString(7, id)
    }

    private fun insert(conn: Connection, auction: Auction) {
        conn.prepareStatement(
            """INSERT INTO auctions
               (id, stall_id, state, start_at, end_at, starting_bid,
                high_bid_amount, high_bidder, high_placed_at, anti_snipe_sec,
                anti_snipe_extend_sec, auction_duration_sec, high_bid_guild_id, opening_round_id)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""
        ).use { ps ->
            bind(ps, auction)
            val rows = ps.executeUpdate()
            if (rows != 1) error("AuctionRepositorySql.create: expected 1 row, affected $rows")
        }
    }

    override fun save(auction: Auction) {
        ds.connection.use { conn ->
            conn.prepareStatement(
                """UPDATE auctions
                   SET state = ?, start_at = ?, end_at = ?, starting_bid = ?,
                       high_bid_amount = ?, high_bidder = ?, high_placed_at = ?,
                       anti_snipe_sec = ?, anti_snipe_extend_sec = ?,
                       auction_duration_sec = ?, high_bid_guild_id = ?
                   WHERE id = ?"""
            ).use { ps ->
                ps.setString(1, auction.state.name)
                ps.setLong(2, auction.startAt.toEpochMilli())
                ps.setLong(3, auction.endAt.toEpochMilli())
                ps.setLong(4, auction.startingBid)
                if (auction.highBid != null) {
                    ps.setLong(5, auction.highBid.amount)
                    ps.setString(6, auction.highBid.bidder.toString())
                    ps.setLong(7, auction.highBid.placedAt.toEpochMilli())
                } else {
                    ps.setNull(5, java.sql.Types.INTEGER)
                    ps.setNull(6, java.sql.Types.VARCHAR)
                    ps.setNull(7, java.sql.Types.BIGINT)
                }
                ps.setLong(8, auction.antiSnipeWindow.toSeconds())
                ps.setLong(9, auction.antiSnipeExtension.toSeconds())
                ps.setLong(10, auction.auctionDuration.toSeconds())
                ps.setString(11, auction.highBid?.guildId)
                ps.setString(12, auction.id.value)
                val rows = ps.executeUpdate()
                if (rows != 1) error("AuctionRepositorySql.save: expected 1 row, affected $rows")
            }
        }
    }

    override fun commitTransition(previous: Auction, next: Auction, award: Stall?, payment: AuctionPayment?) {
        require(previous.state == AuctionState.OPEN && previous.id == next.id)
        ds.connection.use { conn ->
            conn.inMarketTransaction {
                val bid = next.highBid
                conn.prepareStatement("""UPDATE auctions SET state=?, end_at=?, high_bid_amount=?, high_bidder=?,
                    high_placed_at=?, high_bid_guild_id=? WHERE id=? AND state='OPEN'
                    AND COALESCE(high_bid_amount,-1)=?""").use { ps ->
                    ps.setString(1, next.state.name)
                    ps.setLong(2, if (next.endAt == Instant.MAX) Long.MAX_VALUE else next.endAt.toEpochMilli())
                    ps.setObject(3, bid?.amount)
                    ps.setString(4, bid?.bidder?.toString())
                    ps.setObject(5, bid?.placedAt?.toEpochMilli())
                    ps.setString(6, bid?.guildId)
                    ps.setString(7, next.id.value)
                    ps.setLong(8, previous.highBid?.amount ?: -1L)
                    check(ps.executeUpdate() == 1) { "Auction state or high bid changed" }
                }
                if (award != null) {
                    require(award.id == next.stallId && next.state == AuctionState.CLOSED)
                    val stalls = StallRepositorySql(ds)
                    if (award.owner.type == OwnerType.GUILD && next.openingRoundId != null) {
                        stalls.saveGuildAward(conn, award, next.openingRoundId)
                    } else {
                        stalls.save(conn, award)
                    }
                }
                if (payment != null) {
                    require(payment.auctionId == next.id && payment.amount > 0)
                    conn.prepareStatement("""INSERT INTO auction_payments
                        (id,auction_id,purpose,payer_type,recipient,amount,status,next_attempt)
                        VALUES (?,?,?,?,?,?,'PENDING',0)""").use { ps ->
                        ps.setString(1, payment.id)
                        ps.setString(2, payment.auctionId.value)
                        ps.setString(3, payment.purpose)
                        ps.setString(4, payment.recipient.type.name)
                        ps.setString(5, payment.recipient.id)
                        ps.setLong(6, payment.amount)
                        ps.executeUpdate()
                    }
                }
            }
        }
    }

    override fun findExpired(): List<Auction> =
        queryMany("SELECT * FROM auctions WHERE state = 'OPEN' AND end_at <= ?") {
            setLong(1, Instant.now().toEpochMilli())
        }

    override fun findMostRecentClosedByStall(stallId: StallId): Auction? =
        queryOne("SELECT * FROM auctions WHERE stall_id = ? AND state = 'CLOSED' ORDER BY end_at DESC LIMIT 1") {
            setString(1, stallId.value)
        }

    override fun findByStall(stallId: StallId): List<Auction> =
        queryMany("SELECT * FROM auctions WHERE stall_id = ? ORDER BY end_at DESC") {
            setString(1, stallId.value)
        }

    override fun delete(id: AuctionId) {
        ds.connection.use { conn ->
            conn.prepareStatement("DELETE FROM auctions WHERE id = ?").use { ps ->
                ps.setString(1, id.value)
                val rows = ps.executeUpdate()
                if (rows != 1) error("AuctionRepositorySql.delete: expected 1 row, affected $rows")
            }
        }
    }

    private fun bind(ps: PreparedStatement, auction: Auction) {
        ps.setString(1, auction.id.value)
        ps.setString(2, auction.stallId.value)
        ps.setString(3, auction.state.name)
        ps.setLong(4, auction.startAt.toEpochMilli())
        ps.setLong(5, auction.endAt.toEpochMilli())
        ps.setLong(6, auction.startingBid)
        if (auction.highBid != null) {
            ps.setLong(7, auction.highBid.amount)
            ps.setString(8, auction.highBid.bidder.toString())
            ps.setLong(9, auction.highBid.placedAt.toEpochMilli())
        } else {
            ps.setNull(7, java.sql.Types.INTEGER)
            ps.setNull(8, java.sql.Types.VARCHAR)
            ps.setNull(9, java.sql.Types.BIGINT)
        }
        ps.setLong(10, auction.antiSnipeWindow.toSeconds())
        ps.setLong(11, auction.antiSnipeExtension.toSeconds())
        ps.setLong(12, auction.auctionDuration.toSeconds())
        ps.setString(13, auction.highBid?.guildId)
        ps.setString(14, auction.openingRoundId)
    }

    private fun exists(sql: String, prep: PreparedStatement.() -> Unit): Boolean =
        ds.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.prep()
                ps.executeQuery().use { it.next() }
            }
        }

    private fun queryOne(sql: String, prep: PreparedStatement.() -> Unit): Auction? {
        ds.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.prep()
                ps.executeQuery().use { rs ->
                    return if (rs.next()) mapRow(rs) else null
                }
            }
        }
    }

    private fun queryMany(sql: String, prep: PreparedStatement.() -> Unit): List<Auction> {
        ds.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.prep()
                ps.executeQuery().use { rs ->
                    val out = mutableListOf<Auction>()
                    while (rs.next()) out.add(mapRow(rs))
                    return out
                }
            }
        }
    }

    private fun mapRow(rs: ResultSet): Auction {
        val highBidAmount = rs.getLong("high_bid_amount").takeIf { !rs.wasNull() }
        val highBidderStr = rs.getString("high_bidder")
        val highPlacedAtMs = rs.getLong("high_placed_at").takeIf { !rs.wasNull() }
        val highBid = if (highBidAmount != null && highBidderStr != null && highPlacedAtMs != null) {
            Bid(
                bidder = UUID.fromString(highBidderStr),
                amount = highBidAmount,
                placedAt = Instant.ofEpochMilli(highPlacedAtMs),
                guildId = rs.getString("high_bid_guild_id"),
            )
        } else {
            null
        }
        return Auction(
            id = AuctionId(rs.getString("id")),
            stallId = StallId(rs.getString("stall_id")),
            state = AuctionState.valueOf(rs.getString("state")),
            startAt = Instant.ofEpochMilli(rs.getLong("start_at")),
            endAt = Instant.ofEpochMilli(rs.getLong("end_at")),
            startingBid = rs.getLong("starting_bid"),
            highBid = highBid,
            antiSnipeWindow = Duration.ofSeconds(rs.getLong("anti_snipe_sec")),
            antiSnipeExtension = Duration.ofSeconds(rs.getLong("anti_snipe_extend_sec")),
            auctionDuration = Duration.ofSeconds(rs.getLong("auction_duration_sec")),
            openingRoundId = rs.getString("opening_round_id"),
        )
    }
}

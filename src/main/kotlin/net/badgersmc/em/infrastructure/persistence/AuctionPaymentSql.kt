package net.badgersmc.em.infrastructure.persistence

import javax.sql.DataSource
import net.badgersmc.em.domain.auction.AuctionId
import net.badgersmc.em.domain.auction.AuctionPayment
import net.badgersmc.em.domain.auction.AuctionPaymentRepository
import net.badgersmc.em.domain.stall.OwnerRef
import net.badgersmc.em.domain.stall.OwnerType

internal class AuctionPaymentSql(private val ds: DataSource) : AuctionPaymentRepository {
    override fun pendingPayments(now: Long): List<AuctionPayment> = ds.connection.use { conn ->
        conn.prepareStatement("""SELECT * FROM auction_payments WHERE status='PENDING' AND next_attempt<=?
            ORDER BY next_attempt,id LIMIT 32""").use { ps ->
            ps.setLong(1, now)
            ps.executeQuery().use { rows -> buildList {
                while (rows.next()) add(AuctionPayment(AuctionId(rows.getString("auction_id")), rows.getString("purpose"),
                    OwnerRef(OwnerType.valueOf(rows.getString("payer_type")), rows.getString("recipient")),
                    rows.getLong("amount"), rows.getString("id")))
            } }
        }
    }

    override fun claimPayment(id: String): Boolean = ds.connection.use { conn ->
        conn.prepareStatement("UPDATE auction_payments SET status='IN_FLIGHT' WHERE id=? AND status='PENDING'").use { ps ->
            ps.setString(1, id)
            ps.executeUpdate() == 1
        }
    }

    override fun finishPayment(id: String, paid: Boolean, nextAttempt: Long) {
        ds.connection.use { conn ->
            conn.prepareStatement("UPDATE auction_payments SET status=?,next_attempt=? WHERE id=? AND status='IN_FLIGHT'").use { ps ->
                ps.setString(1, if (paid) "PAID" else "PENDING")
                ps.setLong(2, nextAttempt)
                ps.setString(3, id)
                check(ps.executeUpdate() == 1)
            }
        }
    }
}

package net.badgersmc.em.infrastructure.persistence

import net.badgersmc.em.application.ItemStackSerializer
import net.badgersmc.em.domain.auction.Auction
import net.badgersmc.em.domain.auction.AuctionId
import net.badgersmc.em.domain.auction.EmergencyListing
import net.badgersmc.em.domain.shop.SignDirection
import java.sql.Connection
import java.util.Locale

internal object EmergencyListingSql {
    fun capture(connection: Connection, auction: Auction) {
        val select = """SELECT id, direction, sell_material, sell_item, sell_amount, cost_item, cost_amount
            FROM shop_items WHERE stall_id = ? AND admin_shop = 0 AND search_enabled = 1"""
        val insert = """INSERT INTO emergency_listings
            (auction_id, shop_id, direction, item, quantity, payment, amount) VALUES (?, ?, ?, ?, ?, ?, ?)"""
        connection.prepareStatement(select).use { source ->
            source.setString(1, auction.stallId.value)
            source.executeQuery().use { rows -> copyRows(connection, insert, auction, rows) }
        }
    }

    private fun copyRows(connection: Connection, sql: String, auction: Auction, rows: java.sql.ResultSet) {
        connection.prepareStatement(sql).use { target ->
            while (rows.next()) {
                val direction = SignDirection.valueOf(rows.getString("direction"))
                val material = rows.getString("sell_material")?.takeIf { it.isNotBlank() }
                    ?: materialOf(rows.getString("sell_item"))
                val payment = if (direction == SignDirection.TRADE) materialOf(rows.getString("cost_item")) else "raw gold"
                target.setString(1, auction.id.value)
                target.setLong(2, rows.getLong("id"))
                target.setString(3, direction.name)
                target.setString(4, label(material))
                target.setInt(5, rows.getInt("sell_amount"))
                target.setString(6, label(payment))
                target.setInt(7, rows.getInt("cost_amount"))
                target.addBatch()
            }
            target.executeBatch()
        }
    }

    private fun materialOf(serialized: String) = ItemStackSerializer.deserialize(serialized)?.type?.name

    private fun label(material: String?) =
        material?.removePrefix("minecraft:")?.lowercase(Locale.ROOT)?.replace('_', ' ') ?: "unknown item"

    fun page(connection: Connection, id: AuctionId, offset: Long, limit: Int): List<EmergencyListing> {
        val sql = """SELECT direction, item, quantity, payment, amount FROM emergency_listings
            WHERE auction_id = ? ORDER BY shop_id LIMIT ? OFFSET ?"""
        return connection.prepareStatement(sql).use { query ->
            query.setString(1, id.value)
            query.setInt(2, limit)
            query.setLong(3, offset)
            query.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) add(EmergencyListing(SignDirection.valueOf(rows.getString(1)),
                        rows.getString(2), rows.getInt(3), rows.getString(4), rows.getInt(5)))
                }
            }
        }
    }
}

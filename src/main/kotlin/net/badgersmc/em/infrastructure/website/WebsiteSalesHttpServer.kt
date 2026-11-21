package net.badgersmc.em.infrastructure.website

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import net.badgersmc.em.domain.shop.WebsitePublicSale
import net.badgersmc.em.domain.shop.WebsitePublicSalePage
import net.badgersmc.em.domain.shop.WebsitePublicSaleReader
import net.badgersmc.em.websync.WebsiteSyncJson
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal fun salesCurrencyVerified(): Boolean =
    (System.getenv("ENTHUSIA_MARKET_SALES_CURRENCY") ?: System.getProperty("enthusiamarket.sales.currency")) == "RAW_GOLD"

/** Opt-in reader with a loopback default for deployments behind an HTTPS proxy. */
internal class WebsiteSalesHttpServer(private val port: Int, private val token: String,
                                      private val generation: () -> UUID,
                                      private val bindAddress: String = "127.0.0.1",
                                      private val coverage: () -> Pair<Instant, Instant>? = { null },
                                      private val reader: WebsitePublicSaleReader) : AutoCloseable {
    private val executor = ThreadPoolExecutor(1, 2, 30, TimeUnit.SECONDS, ArrayBlockingQueue(16),
        { task -> Thread(task, "EnthusiaMarket-SalesRead").apply { isDaemon = true } }, ThreadPoolExecutor.AbortPolicy())
    private var server: HttpServer? = null

    fun start() {
        require(port in 1..65535)
        require(validToken())
        check(server == null)
        val address = when (bindAddress) {
            "127.0.0.1" -> InetSocketAddress("127.0.0.1", port)
            "0.0.0.0" -> InetSocketAddress("0.0.0.0", port)
            else -> throw IllegalArgumentException("Unsupported sales listener address")
        }
        val listener = HttpServer.create(address, 16)
        try {
            listener.createContext("/api/community/sales", ::handle)
            listener.executor = executor; listener.start(); server = listener
        } catch (failure: Exception) { listener.stop(0); throw failure }
    }

    private fun validToken() = token.length in 32..8192 && token.none { it in "\r\n" }

    private fun handle(exchange: HttpExchange) {
        try {
            val reply = route(exchange)
            respond(exchange, reply.status, reply.payload)
        } catch (_: Exception) {
            try { respond(exchange, 503, mapOf("error" to "sales_unavailable")) } catch (_: Exception) { /* Client disconnected. */ }
        } finally { exchange.close() }
    }

    private data class Reply(val status: Int, val payload: Map<String, Any?>)

    private fun errorReply(status: Int, reason: String) = Reply(status, mapOf("error" to reason))

    private fun route(exchange: HttpExchange): Reply {
        if (exchange.requestURI.path != "/api/community/sales") return errorReply(404, "not_found")
        if (exchange.requestMethod != "GET") return errorReply(405, "method_not_allowed")
        val supplied = exchange.requestHeaders.getFirst("Authorization").orEmpty()
        if (!MessageDigest.isEqual(supplied.toByteArray(Charsets.UTF_8), "Bearer $token".toByteArray(Charsets.UTF_8))) {
            return errorReply(401, "unauthorized")
        }
        val query = exchange.requestURI.rawQuery.orEmpty()
        val before = generation()
        if (query == "protocol=2") return stableReply(before, mapOf("protocol" to 2, "generation" to before.toString()))
        return salesReply(query, before)
    }

    private fun salesReply(query: String, before: UUID): Reply {
        val match = Regex("protocol=2&after=([0-9]{1,19})&generation=([0-9a-f-]{36})").matchEntire(query)
            ?: return errorReply(400, "invalid_request")
        val after = match.groupValues[1].toLongOrNull() ?: return errorReply(400, "invalid_cursor")
        val value = match.groupValues[2]
        val expected = runCatching { UUID.fromString(value) }.getOrNull()
        if (expected?.toString() != value) return errorReply(400, "invalid_generation")
        if (expected != before) return errorReply(409, "generation_changed")
        val window = runCatching { coverage() }.getOrNull()
        val page = reader.readAfter(after)
        validatePage(page, after)
        val items = page.items.map(::salePayload)
        if (generation() != before) return errorReply(409, "generation_changed")
        return Reply(200, mapOf("protocol" to 2, "generation" to value, "items" to items, "complete" to false,
            "nextCursor" to page.nextCursor?.toString(), "checkpointCursor" to page.checkpointCursor.toString(),
            "awardCoverage" to verifiedWindow(page, window)))
    }

    private fun stableReply(before: UUID, payload: Map<String, Any?>): Reply =
        if (generation() == before) Reply(200, payload) else errorReply(409, "generation_changed")

    private fun verifiedWindow(page: WebsitePublicSalePage, window: Pair<Instant, Instant>?): Map<String, String>? {
        if (window == null || page.nextCursor != null) return null
        if (runCatching { coverage()?.first }.getOrNull() != window.first) return null
        return mapOf("from" to window.first.toString(), "through" to window.second.toString())
    }

    private fun validatePage(page: WebsitePublicSalePage, after: Long) {
        require(page.items.size <= 250 && page.checkpointCursor >= after)
        require(page.items.zipWithNext().all { (left, right) -> left.id < right.id })
        require(page.items.all { it.id > after && it.id <= page.checkpointCursor })
        require(page.checkpointCursor == (page.items.lastOrNull()?.id ?: after))
        require(page.nextCursor == null || page.nextCursor == page.checkpointCursor)
    }

    private fun salePayload(sale: WebsitePublicSale): Map<String, Any?> {
        require(sale.quantity in 1..1_000_000_000 && sale.gold in 1..1_000_000_000_000L)
        require(sale.stall.matches(Regex("[A-Za-z0-9_.:-]{1,80}")) && sale.item.matches(Regex("[a-z0-9_]{1,80}")))
        require(sale.guild.length <= 80)
        return mapOf("id" to "market-${sale.recordingId}", "stall" to sale.stall, "guild" to sale.guild,
            "item" to sale.item, "quantity" to sale.quantity, "gold" to sale.gold,
            "occurredAt" to Instant.ofEpochMilli(sale.occurredAt).toString(), "direction" to "SELL", "currency" to "RAW_GOLD",
            "awardOwner" to sale.awardOwner?.let {
                mapOf("id" to it.id.toString(), "name" to it.name, "representative" to it.representative.toString())
            })
    }

    private fun respond(exchange: HttpExchange, status: Int, payload: Any) {
        val bytes = WebsiteSyncJson.bytes(payload)
        require(bytes.size <= 150_000)
        exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
        exchange.responseHeaders.set("Cache-Control", "no-store")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
    override fun close() { server?.stop(0); server = null; executor.shutdownNow() }
}

package net.badgersmc.em.domain.auction

import net.badgersmc.em.domain.stall.StallId
import net.badgersmc.em.domain.stall.Stall

import net.badgersmc.em.domain.shop.SignDirection

data class EmergencyListing(
    val direction: SignDirection,
    val item: String,
    val quantity: Int,
    val payment: String,
    val amount: Int,
)

interface AuctionPaymentRepository {
    fun pendingPayments(now: Long): List<AuctionPayment>
    fun claimPayment(id: String): Boolean
    fun finishPayment(id: String, paid: Boolean, nextAttempt: Long)
}

interface AuctionRepository : AuctionPaymentRepository {
    fun findById(id: AuctionId): Auction?
    fun findOpenByStall(stallId: StallId): Auction?
    fun allOpen(): List<Auction>
    /** Includes expired auctions until settlement resolves their escrow. */
    fun hasOtherOpenGuildBid(guildId: String, auctionId: AuctionId): Boolean
    fun hasGuildOpeningRoundWin(roundId: String, guildId: String): Boolean
    fun findExpired(): List<Auction>
    fun createEmergency(auction: Auction)
    fun previewListings(id: AuctionId, offset: Long, limit: Int): List<EmergencyListing>
    fun create(auction: Auction)
    /** Opens all lots and transitions their stalls together. Returns the first conflicting stall. */
    fun openImportedLots(lots: List<Pair<Stall, Auction>>): StallId?
    fun save(auction: Auction)
    /** Persist the transition, optional ownership award and any resulting obligation atomically. */
    fun commitTransition(previous: Auction, next: Auction, award: Stall?, payment: AuctionPayment?)
    fun delete(id: AuctionId)
    /** Most recent CLOSED auction for [stallId] by end_at desc, or null. */
    fun findMostRecentClosedByStall(stallId: StallId): Auction?
    /** All auctions for [stallId] regardless of state. */
    fun findByStall(stallId: StallId): List<Auction>
}

package net.badgersmc.em.domain.auction

import net.badgersmc.em.domain.stall.OwnerRef
import java.util.UUID

data class AuctionPayment(
    val auctionId: AuctionId,
    val purpose: String,
    val recipient: OwnerRef,
    val amount: Long,
    val id: String = UUID.randomUUID().toString(),
)

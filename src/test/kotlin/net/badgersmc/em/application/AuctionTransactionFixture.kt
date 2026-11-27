package net.badgersmc.em.application

import io.mockk.every
import net.badgersmc.em.domain.auction.Auction
import net.badgersmc.em.domain.auction.AuctionRepository
import net.badgersmc.em.domain.stall.OwnerType
import net.badgersmc.em.domain.stall.Stall
import net.badgersmc.em.domain.stall.StallRepository

internal fun stubAuctionTransactions(auctions: AuctionRepository, stalls: StallRepository) {
    every { auctions.claimPayment(any()) } returns true
    every { auctions.commitTransition(any(), any(), any(), any()) } answers {
        val next = secondArg<Auction>()
        val award = thirdArg<Stall?>()
        if (award != null) {
            if (award.owner.type == OwnerType.GUILD && next.openingRoundId != null)
                stalls.saveOpeningRoundGuildAward(award, next.openingRoundId)
            else stalls.save(award)
        }
        auctions.save(next)
    }
}

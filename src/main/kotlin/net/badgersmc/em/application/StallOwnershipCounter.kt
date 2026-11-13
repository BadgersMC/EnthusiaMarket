package net.badgersmc.em.application

import net.badgersmc.em.domain.stall.OwnerType
import net.badgersmc.em.domain.stall.StallRepository
import net.badgersmc.em.domain.stall.StallState
import net.badgersmc.nexus.annotations.Service
import java.util.UUID

/** Counts a player's personally-owned (SOLO) stalls, total and per region kind (ItemShops parity SP4). */
@Service
class StallOwnershipCounter(private val stalls: StallRepository) {

    data class OwnedCounts(val total: Int, val byKind: Map<String, Int>)

    fun counts(player: UUID): OwnedCounts {
        val owned = stalls.all().filter {
            it.owner.type == OwnerType.SOLO &&
                it.owner.id == player.toString() &&
                (it.state == StallState.OWNED || it.state == StallState.GRACE)
        }
        return OwnedCounts(total = owned.size, byKind = owned.groupingBy { it.kind }.eachCount())
    }

    /** Stalls owned (or in grace) by guild [guildId] (REQ-399). */
    fun guildCount(guildId: String): Int = stalls.all().count {
        it.owner.type == OwnerType.GUILD && it.owner.id == guildId &&
            (it.state == StallState.OWNED || it.state == StallState.GRACE)
    }
}

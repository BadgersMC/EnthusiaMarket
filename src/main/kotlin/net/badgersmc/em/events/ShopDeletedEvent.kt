package net.badgersmc.em.events

import net.badgersmc.em.domain.shop.Shop
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.UUID

/**
 * Called after a shop has been deleted (REQ-026).
 */
class ShopDeletedEvent @JvmOverloads constructor(
    val ownerId: UUID,
    val deletedShop: Shop? = null,
) : Event() {
    override fun getHandlers(): HandlerList = handlerList

    companion object {
        @JvmStatic
        val handlerList = HandlerList()
    }
}
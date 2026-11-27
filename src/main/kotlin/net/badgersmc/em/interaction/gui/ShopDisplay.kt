package net.badgersmc.em.interaction.gui

import net.badgersmc.em.domain.shop.Shop
import net.badgersmc.em.domain.shop.SignDirection

/** Shared display helpers for shop menus — avoids duplicating direction/stock formatting. */
object ShopDisplay {

    fun directionLabel(direction: SignDirection): String = when (direction) {
        SignDirection.SELL -> "<#B6D7A8>Sell</#B6D7A8>"
        SignDirection.BUY -> "<#FFCF1F>Buy</#FFCF1F>"
        SignDirection.TRADE -> "<#F2B66D>Trade</#F2B66D>"
    }

    fun tradesAvailable(shop: Shop): Int = when (shop.direction) {
        SignDirection.BUY -> Int.MAX_VALUE // BUY capacity and affordability are service-owned checks
        else -> if (shop.adminShop) Int.MAX_VALUE else if (shop.sellAmount > 0) shop.stockCount / shop.sellAmount else 0
    }
}

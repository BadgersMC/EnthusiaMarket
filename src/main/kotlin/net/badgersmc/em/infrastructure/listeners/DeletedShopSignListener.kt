package net.badgersmc.em.infrastructure.listeners

import net.badgersmc.em.domain.shop.Shop
import net.badgersmc.em.domain.shop.ShopRepository
import net.badgersmc.em.events.ShopDeletedEvent
import net.badgersmc.nexus.annotations.Component
import net.kyori.adventure.text.Component as Text
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.block.Sign
import org.bukkit.block.data.type.WallSign
import org.bukkit.block.sign.Side
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

@net.badgersmc.nexus.paper.listeners.Listener
@Component
class DeletedShopSignListener(private val shops: ShopRepository) : Listener {
    @EventHandler
    fun onDeleted(event: ShopDeletedEvent) {
        val shop = event.deletedShop ?: return
        val world = Bukkit.getWorld(shop.signWorld) ?: return
        val x = shop.signX shr 4
        val z = shop.signZ shr 4
        if (world.isChunkLoaded(x, z)) {
            clearListing(world, shop)
        } else {
            // Paper completes the chunk future on the main thread. Never generate new terrain.
            clearAfterLoad(world, shop)
        }
    }

    private fun attachedToContainer(shop: Shop, facing: org.bukkit.block.BlockFace) =
        shop.signX - facing.modX == shop.containerX && shop.signY - facing.modY == shop.containerY &&
            shop.signZ - facing.modZ == shop.containerZ

    internal fun clearListing(world: World, shop: Shop) {
        if (shops.findBySign(shop.signWorld, shop.signX, shop.signY, shop.signZ) != null) return
        val block = world.getBlockAt(shop.signX, shop.signY, shop.signZ)
        val facing = (block.blockData as? WallSign)?.facing ?: return
        if (shop.containerWorld != shop.signWorld) return
        if (!attachedToContainer(shop, facing)) return
        val sign = block.state as? Sign ?: return
        clearListingText(sign, shop)
    }

    private fun clearListingText(sign: Sign, shop: Shop) {
        val side = sign.getSide(Side.FRONT)
        val header = PlainTextComponentSerializer.plainText().serialize(side.line(0))
        if (header != "[${shop.direction.name}]") return
        for (line in 0..3) side.line(line, Text.empty())
        sign.update(false, false)
    }

    private fun clearAfterLoad(world: World, shop: Shop) {
        world.getChunkAtAsync(shop.signX shr 4, shop.signZ shr 4, false).thenAccept { chunk ->
            if (chunk != null) clearListing(world, shop)
        }
    }
}

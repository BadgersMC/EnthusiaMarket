package net.badgersmc.em.application

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

/** Byte-exact item matching for shop stock/cost checks (ignores stack size). */
object ItemStackMatch {

    fun matches(a: ItemStack, b: ItemStack): Boolean =
        normalizedBytes(a).contentEquals(normalizedBytes(b))

    fun countIn(inventory: Inventory, template: ItemStack): Int =
        inventory.contents.filterNotNull()
            .filter { matches(it, template) }
            .sumOf { it.amount }

    fun containsAtLeast(inventory: Inventory, template: ItemStack, amount: Int): Boolean =
        countIn(inventory, template) >= amount

    fun canFit(inventory: Inventory, template: ItemStack, amount: Int): Boolean {
        if (amount <= 0) return false
        var remaining = amount
        val maxStack = template.maxStackSize
        for (slot in inventory.storageContents) {
            if (remaining <= 0) break
            if (slot == null || slot.type.isAir) {
                remaining -= maxStack
                continue
            }
            if (matches(slot, template)) {
                remaining -= (maxStack - slot.amount).coerceAtLeast(0)
            }
        }
        return remaining <= 0
    }

    private fun normalizedBytes(stack: ItemStack): ByteArray {
        val single = stack.clone()
        single.amount = 1
        return single.serializeAsBytes()
    }
}
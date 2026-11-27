package net.badgersmc.em.application

import org.bukkit.inventory.ItemStack

/**
 * Transfers [amount] items matching [template] (by [ItemStack.isSimilar]) from
 * [source] to [dest], preserving the original item stacks including display names
 * and NBT. Atomic — preflights source count and dest capacity before mutating
 * either inventory. Returns [TransferResult.Success] when the full amount was
 * transferred.
 */
internal sealed class TransferResult {
    data object Success : TransferResult()
    data class SourceFailure(val leftover: Map<Int, ItemStack>) : TransferResult()
    data class DestFull(val leftover: Map<Int, ItemStack>) : TransferResult()
}

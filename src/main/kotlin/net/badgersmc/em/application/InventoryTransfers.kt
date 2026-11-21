package net.badgersmc.em.application

import org.bukkit.inventory.Inventory
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

@Suppress("CyclomaticComplexMethod")
internal fun transferSimilar(
    source: Inventory,
    dest: Inventory,
    template: ItemStack,
    amount: Int,
): TransferResult {
    val contents = source.storageContents
    if (contents != null && contents.isNotEmpty()) {
        // Preflight: don't mutate until we know the full amount can be moved
        if (!ItemStackMatch.containsAtLeastSimilar(source, template, amount)) {
            val fail = HashMap<Int, ItemStack>()
            fail[0] = template.clone().apply { this.amount = amount }
            return TransferResult.SourceFailure(fail)
        }
        if (!ItemStackMatch.canFitSimilar(dest, template, amount)) {
            val fail = HashMap<Int, ItemStack>()
            fail[0] = template.clone().apply { this.amount = amount }
            return TransferResult.DestFull(fail)
        }
        moveMatchingStacks(TransferInventories(source, dest, contents), template, amount)?.let { return it }
    }
    // Source has no contents (likely a test mock) — fall back to Bukkit removeItem
    val batch = template.clone().apply { this.amount = amount }
    val removed = source.removeItem(batch)
    if (removed.isNotEmpty()) return TransferResult.SourceFailure(removed)
    val leftover = dest.addItem(batch)
    if (leftover.isNotEmpty()) {
        source.addItem(batch)
        return TransferResult.DestFull(leftover)
    }
    return TransferResult.Success
}

private data class TransferInventories(val source: Inventory, val dest: Inventory, val contents: Array<out ItemStack?>)

private fun moveMatchingStacks(inventories: TransferInventories, template: ItemStack, amount: Int): TransferResult? {
    val (source, dest, contents) = inventories
    val sourceBefore = contents.map { it?.clone() }.toTypedArray()
    val destBefore = dest.storageContents.map { it?.clone() }.toTypedArray()
    var remaining = amount
    for (item in contents) {
        if (item == null) continue
        if (!ItemStackMatch.isSimilarIgnoringDamageNullZero(item, template)) continue
        val take = minOf(item.amount, remaining)
        val batch = item.clone().apply { this.amount = take }
        val removed = source.removeItem(batch.clone())
        if (removed.isNotEmpty()) {
            source.storageContents = sourceBefore
            dest.storageContents = destBefore
            return TransferResult.SourceFailure(removed)
        }
        val overflow = dest.addItem(batch)
        if (overflow.isNotEmpty()) {
            source.storageContents = sourceBefore
            dest.storageContents = destBefore
            return TransferResult.DestFull(overflow)
        }
        remaining -= take
        if (remaining <= 0) return TransferResult.Success
    }
    return null
}

/**
 * Removes items matching [template] from [source] using
 * [ItemStackMatch.isSimilarIgnoringDamageNullZero] instead of Bukkit's
 * strict equality. Clones matched items from the inventory so their
 * real metadata (including nullable Damage tag) is preserved in the
 * removeItem call. Returns leftovers if the full amount can't be removed.
 */
@Suppress("CyclomaticComplexMethod")
internal fun removeSimilar(source: Inventory, template: ItemStack, amount: Int): Map<Int, ItemStack> {
    var remaining = amount
    val contents = source.contents
    if (contents != null && contents.isNotEmpty()) {
        for (item in contents) {
            if (item == null) continue
            if (!ItemStackMatch.isSimilarIgnoringDamageNullZero(item, template)) continue
            val take = minOf(item.amount, remaining)
            val batch = item.clone().apply { this.amount = take }
            source.removeItem(batch)
            remaining -= take
            if (remaining <= 0) return emptyMap()
        }
    }
    if (remaining <= 0) return emptyMap()
    // Source has no contents (likely a test mock) — fall back to Bukkit removeItem
    val batch = template.clone().apply { this.amount = remaining }
    return source.removeItem(batch)
}

/**
 * Like [removeSimilar] but also collects the cloned items that were removed
 * from the source inventory (preserving their real NBT/components).
 * REQ-301: returned collected items are the actual container items, to be
 * delivered to the player instead of the deserialized template.
 * Returns (collected: List<ItemStack>, leftovers: Map<Int, ItemStack>).
 */
internal fun removeAndCollectSimilar(source: Inventory, template: ItemStack, amount: Int): Pair<List<ItemStack>, Map<Int, ItemStack>> {
    val collected = mutableListOf<ItemStack>()
    var remaining = amount
    val contents = source.contents
    if (contents != null && contents.isNotEmpty()) {
        for (item in contents) {
            if (item == null) continue
            if (!ItemStackMatch.isSimilarIgnoringDamageNullZero(item, template)) continue
            val take = minOf(item.amount, remaining)
            val batch = item.clone().apply { this.amount = take }
            source.removeItem(batch)
            collected.add(batch)
            remaining -= take
            if (remaining <= 0) return collected to emptyMap()
        }
    }
    if (remaining <= 0) return collected to emptyMap()
    // Fallback for test mocks — use template
    val batch = template.clone().apply { this.amount = remaining }
    val leftovers = source.removeItem(batch)
    if (leftovers.isEmpty()) collected.add(batch)
    return collected to leftovers
}

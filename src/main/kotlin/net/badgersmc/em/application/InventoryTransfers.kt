package net.badgersmc.em.application

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

@Suppress("CyclomaticComplexMethod")
internal fun transferSimilar(
    source: Inventory,
    dest: Inventory,
    template: ItemStack,
    amount: Int,
): TransferResult {
    val contents = source.storageContents
    if (!contents.isNullOrEmpty()) {
        val rejected = transferRejection(source, dest, template, amount)
        if (rejected != null) return rejected
        moveMatchingStacks(TransferInventories(source, dest, contents), template, amount)?.let { return it }
    }
    return transferBatch(source, dest, template, amount)
}

private fun transferRejection(source: Inventory, dest: Inventory, template: ItemStack, amount: Int): TransferResult? {
    fun remainder() = mapOf(0 to template.clone().apply { this.amount = amount })
    if (!ItemStackMatch.containsAtLeastSimilar(source, template, amount)) return TransferResult.SourceFailure(remainder())
    if (!ItemStackMatch.canFitSimilar(dest, template, amount)) return TransferResult.DestFull(remainder())
    return null
}

private fun transferBatch(source: Inventory, dest: Inventory, template: ItemStack, amount: Int): TransferResult {
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
    fun rollback(result: TransferResult): TransferResult {
        source.storageContents = sourceBefore
        dest.storageContents = destBefore
        return result
    }
    var remaining = amount
    for (item in matchingItems(contents, template)) {
        val take = minOf(item.amount, remaining)
        val batch = item.clone().apply { this.amount = take }
        val removed = source.removeItem(batch.clone())
        if (removed.isNotEmpty()) return rollback(TransferResult.SourceFailure(removed))
        val overflow = dest.addItem(batch)
        if (overflow.isNotEmpty()) return rollback(TransferResult.DestFull(overflow))
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
    return removeAndCollectSimilar(source, template, amount).second
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
    for (item in matchingItems(source.contents, template)) {
        val take = minOf(item.amount, remaining)
        val batch = item.clone().apply { this.amount = take }
        source.removeItem(batch)
        collected.add(batch)
        remaining -= take
        if (remaining <= 0) return collected to emptyMap()
    }
    if (remaining <= 0) return collected to emptyMap()
    // Fallback for test mocks — use template
    val batch = template.clone().apply { this.amount = remaining }
    val leftovers = source.removeItem(batch)
    if (leftovers.isEmpty()) collected.add(batch)
    return collected to leftovers
}

private fun matchingItems(contents: Array<out ItemStack?>?, template: ItemStack): Sequence<ItemStack> =
    contents.orEmpty().asSequence().filterNotNull().filter { ItemStackMatch.isSimilarIgnoringDamageNullZero(it, template) }

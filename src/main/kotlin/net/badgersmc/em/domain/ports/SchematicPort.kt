package net.badgersmc.em.domain.ports

import org.bukkit.World
import java.io.File

/**
 * Domain port for stall schematic capture and restore (TDD-270/271).
 *
 * Backed by [net.badgersmc.em.infrastructure.worldedit.WorldEditSchematicAdapter]
 * in the infrastructure layer. Decoupled so domain/application code never
 * imports WE or FAWE directly.
 *
 * Callers treat every result as best-effort — a failed capture on import is
 * logged and skipped; a failed restore on sellback is logged and the sellback
 * still completes (DB is authoritative, schematics are cosmetic recovery).
 */
interface SchematicPort {
    sealed interface Result {
        data object Success : Result
        data class Failure(val cause: Throwable) : Result
    }

    /**
     * Capture the current state of [regionId] in [world] to [outputFile].
     * Creates parent directories automatically.
     */
    fun capture(regionId: String, world: World, outputFile: File): Result

    /**
     * Restore [regionId] in [world] from [sourceFile].
     * When [async] is true the paste is dispatched asynchronously (default).
     */
    fun restore(regionId: String, world: World, sourceFile: File, async: Boolean = true): Result
}

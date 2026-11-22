package net.badgersmc.em.infrastructure.worldguard

import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard
import com.sk89q.worldedit.function.operation.ForwardExtentCopy
import com.sk89q.worldedit.function.operation.Operations
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.regions.CuboidRegion
import com.sk89q.worldedit.extent.Extent
import com.sk89q.worldedit.regions.Region
import com.sk89q.worldedit.util.SideEffectSet
import com.sk89q.worldguard.protection.regions.ProtectedRegion
import com.sk89q.worldguard.WorldGuard
import net.badgersmc.em.config.EnthusiaMarketConfig
import net.badgersmc.em.domain.ports.SchematicService
import net.badgersmc.nexus.annotations.Component
import net.badgersmc.nexus.worldedit.WorldEditAdapter
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import java.io.File

/**
 * Infrastructure adapter implementing the EM domain [SchematicService] port
 * (REQ-270..274) by driving WorldEdit/FAWE through the nexus-worldedit facade.
 *
 * Region geometry is resolved via WorldGuard's `RegionContainer`: the stall's
 * WorldGuard region min/max define the cuboid copied into a clipboard on
 * capture. Restore writes only blocks contained in the current stall region. WE/FAWE selection is handled by
 * [WorldEditAdapter.isFawePresent] (REQ-272) — restores dispatch through the
 * Bukkit scheduler off the main thread when FAWE is present and run inline
 * otherwise.
 *
 * The domain layer never sees WorldEdit/WorldGuard types; only [SchematicService]
 * crosses the boundary.
 */
@Component
class WorldEditSchematicAdapter(
    private val plugin: Plugin,
    private val config: EnthusiaMarketConfig,
) : SchematicService {

    private fun schemFile(stallId: String): File =
        File(File(plugin.dataFolder, config.schematics.directory), "$stallId.schem")

    override fun capture(stallId: String, world: String, regionId: String): SchematicService.Result {
        if (!config.schematics.enabled) return SchematicService.Result.Disabled
        return try {
            val file = schemFile(stallId)
            // Idempotent per stall lifetime: never overwrite an existing snapshot
            // (REQ-270 "for the first time").
            if (file.exists()) return SchematicService.Result.Success

            val bukkitWorld = Bukkit.getWorld(world)
                ?: return SchematicService.Result.Failure(
                    IllegalStateException("World '$world' is not loaded")
                )
            val boundary = stallRegion(world, regionId)
                ?: return SchematicService.Result.Failure(
                    IllegalStateException("Region '$regionId' not found in world '$world'")
                )

            val weWorld = BukkitAdapter.adapt(bukkitWorld)
            val region = CuboidRegion(weWorld, boundary.minimumPoint, boundary.maximumPoint)
            val clipboard = BlockArrayClipboard(region)
            clipboard.origin = region.minimumPoint
            WorldEdit.getInstance().newEditSession(weWorld).use { editSession ->
                val copy = ForwardExtentCopy(editSession, region, clipboard, region.minimumPoint)
                copy.isCopyingEntities = false
                Operations.complete(copy)
            }
            WorldEditAdapter.saveSchematic(clipboard, file)
            SchematicService.Result.Success
        } catch (e: Exception) {
            SchematicService.Result.Failure(e)
        }
    }

    override fun restore(stallId: String, world: String, regionId: String): SchematicService.Result {
        if (!config.schematics.enabled) return SchematicService.Result.Disabled
        return try {
            val file = schemFile(stallId)
            if (!file.exists()) {
                return SchematicService.Result.Failure(
                    IllegalStateException("No snapshot for stall '$stallId' at ${file.absolutePath}")
                )
            }
            Bukkit.getWorld(world)
                ?: return SchematicService.Result.Failure(
                    IllegalStateException("World '$world' is not loaded")
                )
            // Validate the region exists up front so a missing region fails fast
            // (synchronously) rather than inside an async paste.
            val boundary = stallRegion(world, regionId)
                ?: return SchematicService.Result.Failure(
                    IllegalStateException("Region '$regionId' not found in world '$world'")
                )

            if (WorldEditAdapter.isFawePresent) {
                // FAWE manages its own async queue; dispatch off the main thread
                // so the paste never blocks the tick loop (REQ-272).
                object : BukkitRunnable() {
                    override fun run() {
                        runCatching { pasteNow(world, file, boundary) }.onFailure {
                            plugin.logger.warning(
                                "Async schematic restore failed for stall '$stallId': ${it.message}"
                            )
                        }
                    }
                }.runTaskAsynchronously(plugin)
            } else {
                pasteNow(world, file, boundary)
            }
            SchematicService.Result.Success
        } catch (e: Exception) {
            SchematicService.Result.Failure(e)
        }
    }

    private fun pasteNow(world: String, file: File, boundary: ProtectedRegion) {
        val bukkitWorld = Bukkit.getWorld(world) ?: error("World '$world' is not loaded")
        val weWorld = BukkitAdapter.adapt(bukkitWorld)
        val clipboard = WorldEditAdapter.loadSchematic(file)
        WorldEdit.getInstance().newEditSession(weWorld).use { editSession ->
            editSession.setSideEffectApplier(SideEffectSet.none())
            restoreStallBlocks(clipboard, clipboard.region, editSession, boundary)
        }
    }

    /** Resolve the named WorldGuard stall geometry, or null if absent. */
    private fun stallRegion(world: String, regionId: String): ProtectedRegion? {
        val bukkitWorld = Bukkit.getWorld(world) ?: return null
        val container = WorldGuard.getInstance().platform.regionContainer
        val regionManager = container.get(BukkitAdapter.adapt(bukkitWorld)) ?: return null
        return regionManager.getRegion(regionId)
    }
}

/** Legacy files contain the bounding box. Clip writes in world coordinates, including writes of air. */
internal fun restoreStallBlocks(source: Extent, saved: Region, target: Extent, boundary: ProtectedRegion) {
    // FAWE can deserialize a stall capture as a zero-based clipboard. Market captured
    // it from the stall minimum. Only use that anchor when all three dimensions match.
    val offset = if (saved.minimumPoint == BlockVector3.ZERO && boundary.minimumPoint != BlockVector3.ZERO) {
        require(saved.maximumPoint == boundary.maximumPoint.subtract(boundary.minimumPoint)) {
            "Local schematic dimensions differ from the current stall"
        }
        boundary.minimumPoint
    } else BlockVector3.ZERO
    val min = saved.minimumPoint.add(offset).getMaximum(boundary.minimumPoint)
    val max = saved.maximumPoint.add(offset).getMinimum(boundary.maximumPoint)
    require(min.x() <= max.x() && min.y() <= max.y() && min.z() <= max.z()) { "Stall ${boundary.minimumPoint}..${boundary.maximumPoint} and schematic ${saved.minimumPoint}..${saved.maximumPoint} do not overlap" }
    for (position in CuboidRegion(min, max)) {
        val local = position.subtract(offset)
        if (boundary.contains(position) && saved.contains(local)) {
            target.setBlock(position, source.getFullBlock(local))
        }
    }
}

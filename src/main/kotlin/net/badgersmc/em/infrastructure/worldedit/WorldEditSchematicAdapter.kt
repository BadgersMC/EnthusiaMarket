package net.badgersmc.em.infrastructure.worldedit

import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard
import com.sk89q.worldedit.function.operation.ForwardExtentCopy
import com.sk89q.worldedit.function.operation.Operations
import com.sk89q.worldedit.regions.CuboidRegion
import com.sk89q.worldedit.session.ClipboardHolder
import com.sk89q.worldguard.WorldGuard
import net.badgersmc.em.domain.ports.SchematicPort
import net.badgersmc.nexus.annotations.Component
import net.badgersmc.nexus.worldedit.WorldEditAdapter
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.bukkit.World
import java.io.File
import java.util.logging.Logger

/**
 * Bridges [SchematicPort] to [WorldEditAdapter] (nexus-worldedit).
 * Uses FAWE when present, falls back to vanilla WE. Async pastes are
 * dispatched via Bukkit's scheduler so EM never imports FAWE directly.
 *
 * WE + WG are compileOnly — only instantiated when both are loaded
 * (guarded in plugin bootstrap).
 */
@Component
class WorldEditSchematicAdapter(
    private val plugin: Plugin,
) : SchematicPort {

    private val log = Logger.getLogger(javaClass.name)

    override fun capture(regionId: String, world: World, outputFile: File): SchematicPort.Result {
        return runCatching {
            val weWorld = BukkitAdapter.adapt(world)

            val wgRegion = WorldGuard.getInstance()
                .platform.regionContainer.get(weWorld)
                ?.getRegion(regionId)
                ?: error("Region $regionId not found in ${world.name}")

            val min = wgRegion.minimumPoint
            val max = wgRegion.maximumPoint
            val cuboid = CuboidRegion(weWorld, min, max)

            val clipboard = BlockArrayClipboard(cuboid)
            WorldEdit.getInstance().newEditSession(weWorld).use { session ->
                val copy = ForwardExtentCopy(session, cuboid, clipboard, min)
                copy.isCopyingEntities = false
                Operations.complete(copy)
            }

            WorldEditAdapter.saveSchematic(clipboard, outputFile)
            log.fine("Captured schematic: region=$regionId -> ${outputFile.path}")
        }.fold(
            onSuccess = { SchematicPort.Result.Success },
            onFailure = { e ->
                log.warning("Schematic capture failed for $regionId: ${e.message}")
                SchematicPort.Result.Failure(e)
            }
        )
    }

    override fun restore(regionId: String, world: World, sourceFile: File, async: Boolean): SchematicPort.Result {
        return runCatching {
            val clipboard = WorldEditAdapter.loadSchematic(sourceFile)
            val weWorld = BukkitAdapter.adapt(world)
            val origin = clipboard.region.minimumPoint

            val doPaste: () -> Unit = {
                WorldEdit.getInstance().newEditSession(weWorld).use { session ->
                    val paste = ClipboardHolder(clipboard)
                        .createPaste(session)
                        .to(origin)
                        .ignoreAirBlocks(false)
                        .build()
                    Operations.complete(paste)
                }
                log.fine("Restored schematic: region=$regionId <- ${sourceFile.path}")
            }

            if (async) {
                // Dispatch async via Bukkit scheduler — works with FAWE and vanilla WE.
                Bukkit.getScheduler().runTaskAsynchronously(plugin, doPaste)
            } else {
                doPaste()
            }
        }.fold(
            onSuccess = { SchematicPort.Result.Success },
            onFailure = { e ->
                log.warning("Schematic restore failed for $regionId: ${e.message}")
                SchematicPort.Result.Failure(e)
            }
        )
    }
}

package net.badgersmc.em.application

import net.badgersmc.em.config.EnthusiaMarketConfig
import net.badgersmc.em.domain.ports.RegionProvider
import net.badgersmc.em.domain.ports.SchematicPort
import net.badgersmc.em.domain.stall.*
import net.badgersmc.nexus.annotations.Service
import org.bukkit.Bukkit
import java.io.File
import java.util.logging.Logger

@Service
class ImportStallsService(
    private val regions: RegionProvider,
    private val stalls: StallRepository,
    private val defaultRent: RentTerms,
    private val config: EnthusiaMarketConfig,
    private val dataFolder: File,
    /** Null when WE/FAWE is not present on the server — capture is skipped. */
    private val schematics: SchematicPort?,
) {
    data class Result(val created: Int, val skipped: Int, val capturedSchematics: Int, val schematicErrors: Int)

    private val log = Logger.getLogger(javaClass.name)

    @Suppress("NestedBlockDepth")
    fun import(world: String, prefix: String): Result {
        var created = 0
        var skipped = 0
        var captured = 0
        var schematicErrors = 0

        for (ref in regions.listByPrefix(world, prefix)) {
            if (stalls.findByRegion(ref.world, ref.id) != null) {
                skipped++
                continue
            }
            stalls.create(
                Stall(
                    id = StallId(ref.id),
                    regionId = ref.id,
                    world = ref.world,
                    state = StallState.UNOWNED,
                    owner = OwnerRef.unowned(),
                    ownerSince = null,
                    winningBid = 0L,
                    rentTerms = defaultRent
                )
            )
            created++

            // TDD-270: Capture pristine schematic on first import so we have
            // a baseline to restore to when a stall is sold back (TDD-271).
            if (config.schematics.enabled && schematics != null) {
                val bukkitWorld = Bukkit.getWorld(ref.world)
                if (bukkitWorld == null) {
                    log.warning("ImportStallsService: world ${ref.world} not loaded; skipping schematic capture for ${ref.id}")
                    schematicErrors++
                } else {
                    val outFile = schematicFile(ref.id)
                    if (outFile.exists()) {
                        log.fine("Schematic already exists for ${ref.id}; skipping capture")
                    } else {
                        when (val r = schematics.capture(ref.id, bukkitWorld, outFile)) {
                            is SchematicPort.Result.Success -> captured++
                            is SchematicPort.Result.Failure -> {
                                log.warning("Schematic capture failed for ${ref.id}: ${r.cause.message}")
                                schematicErrors++
                            }
                        }
                    }
                }
            }
        }
        return Result(created, skipped, captured, schematicErrors)
    }

    /** Returns the canonical schematic [File] path for [regionId]. */
    fun schematicFile(regionId: String): File =
        File(dataFolder, "${config.schematics.directory}/$regionId.schem")
}

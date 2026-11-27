package net.badgersmc.em.infrastructure.listeners

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.math.BlockVector2
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.protection.managers.RegionManager
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion
import com.sk89q.worldguard.protection.regions.ProtectedRegion
import net.badgersmc.em.config.EnthusiaMarketConfig
import net.badgersmc.em.domain.ports.RegionProvisioner
import org.bukkit.Bukkit

/** WorldGuard validation and persistence adapter for the interactive editor. */
internal class StallRegionWorldGuardWriter(
    private val config: EnthusiaMarketConfig,
    private val provisioner: RegionProvisioner,
) {
    private data class FootprintBounds(val minX: Int, val maxX: Int, val minZ: Int, val maxZ: Int) {
        val area: Long get() = (maxX.toLong() - minX + 1) * (maxZ.toLong() - minZ + 1)
    }
    private data class RegionPlan(val manager: RegionManager, val previous: ProtectedRegion?, val replacement: ProtectedRegion)
    private data class PlanResult(val plan: RegionPlan? = null, val error: String? = null)

    fun validate(draft: StallRegionDraft): String? {
        if (draft.points.size < MIN_POINTS || !StallPolygonGeometry.valid(draft.points)) return "stall_region.need_polygon"
        val floor = draft.floorY ?: return "stall_region.mark_floor_first"
        val top = draft.topY ?: return "stall_region.height_required"
        if (top <= floor) return "stall_region.bad_height"
        val manager = manager(draft) ?: return managerError(draft)
        val parent = manager.getRegion(config.market.parentRegion) ?: return "stall_region.parent_missing"
        val polygon = createPolygon(draft, floor, top) ?: return "stall_region.invalid_polygon"
        val bounds = bounds(draft)
        if (bounds.area > MAX_AREA) return "stall_region.area_limit"
        return if (allColumnsInsideParent(polygon, parent, bounds, floor + 1, top)) null else "stall_region.outside_parent"
    }

    fun save(draft: StallRegionDraft): String {
        validate(draft)?.let { return it }
        val manager = manager(draft) ?: return managerError(draft)
        if (manager.getRegion(draft.id) == null && manager.getRegion(config.market.parentRegion) == null) {
            return "stall_region.parent_missing"
        }
        val result = plan(draft, manager)
        result.error?.let { return it }
        val plan = result.plan ?: return "stall_region.invalid_polygon"
        manager.addRegion(plan.replacement)
        return persist(draft, plan)
    }

    private fun manager(draft: StallRegionDraft): RegionManager? {
        val world = Bukkit.getWorld(draft.world) ?: return null
        return WorldGuard.getInstance().platform.regionContainer.get(BukkitAdapter.adapt(world))
    }

    private fun managerError(draft: StallRegionDraft): String =
        if (Bukkit.getWorld(draft.world) == null) "stall_region.world_missing" else "stall_region.manager_missing"

    private fun createPolygon(draft: StallRegionDraft, floor: Int, top: Int): ProtectedPolygonalRegion? =
        runCatching { ProtectedPolygonalRegion(draft.id, draft.points.map { BlockVector2.at(it.x, it.z) }, floor + 1, top) }.getOrNull()

    private fun bounds(draft: StallRegionDraft): FootprintBounds = FootprintBounds(
        draft.points.minOf { it.x }, draft.points.maxOf { it.x }, draft.points.minOf { it.z }, draft.points.maxOf { it.z },
    )

    private fun allColumnsInsideParent(
        polygon: ProtectedPolygonalRegion,
        parent: ProtectedRegion,
        bounds: FootprintBounds,
        bottomY: Int,
        topY: Int,
    ): Boolean = (bounds.minX..bounds.maxX).all { x -> (bounds.minZ..bounds.maxZ).all { z ->
        val bottom = BlockVector3.at(x, bottomY, z)
        !polygon.contains(bottom) || (parent.contains(bottom) && parent.contains(BlockVector3.at(x, topY, z)))
    } }

    private fun plan(draft: StallRegionDraft, manager: RegionManager): PlanResult {
        val floor = draft.floorY ?: return PlanResult(error = "stall_region.mark_floor_first")
        val top = draft.topY ?: return PlanResult(error = "stall_region.height_required")
        val previous = manager.getRegion(draft.id)
        val replacement = createPolygon(draft, floor, top) ?: return PlanResult(error = "stall_region.invalid_polygon")
        if (previous != null) replacement.copyFrom(previous)
        else {
            val parent = manager.getRegion(config.market.parentRegion) ?: return PlanResult(error = "stall_region.parent_missing")
            replacement.priority = config.market.stallPriority
            if (runCatching { replacement.parent = parent }.isFailure) return PlanResult(error = "stall_region.parent_invalid")
        }
        return PlanResult(RegionPlan(manager, previous, replacement))
    }

    private fun persist(draft: StallRegionDraft, plan: RegionPlan): String = try {
        if (plan.previous == null && !provisioner.provision(draft.world, draft.id, config.market.stallPriority)) {
            plan.manager.removeRegion(draft.id)
            return "stall_region.provision_failed"
        }
        plan.manager.save()
        "stall_region.saved"
    } catch (_: Exception) {
        if (plan.previous == null) plan.manager.removeRegion(draft.id) else plan.manager.addRegion(plan.previous)
        runCatching { plan.manager.save() }
        "stall_region.save_failed"
    }

    companion object {
        private const val MIN_POINTS = 3
        private const val MAX_AREA = 16_384L
    }
}

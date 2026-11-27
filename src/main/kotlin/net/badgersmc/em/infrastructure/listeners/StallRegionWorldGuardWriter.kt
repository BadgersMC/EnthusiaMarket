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
    private data class ParentBoundsCheck(
        val polygon: ProtectedPolygonalRegion,
        val parent: ProtectedRegion,
        val bounds: FootprintBounds,
        val bottomY: Int,
        val topY: Int,
    )
    private data class RegionPlan(val manager: RegionManager, val previous: ProtectedRegion?, val replacement: ProtectedRegion)
    private data class PlanResult(val plan: RegionPlan? = null, val error: String? = null)

    fun validate(draft: StallRegionDraft): String? {
        validateDraft(draft)?.let { return it }
        return validateParentMembership(draft)
    }

    private fun validateDraft(draft: StallRegionDraft): String? {
        if (!validPoints(draft)) return "stall_region.need_polygon"
        return validateVerticalBounds(draft)
    }

    private fun validateParentMembership(draft: StallRegionDraft): String? {
        val manager = manager(draft) ?: return managerError(draft)
        val parent = manager.getRegion(config.market.parentRegion) ?: return "stall_region.parent_missing"
        val polygon = createPolygon(draft) ?: return "stall_region.invalid_polygon"
        val bounds = bounds(draft)
        return validateFootprint(polygon, parent, bounds, draft)
    }

    private fun validateFootprint(
        polygon: ProtectedPolygonalRegion,
        parent: ProtectedRegion,
        bounds: FootprintBounds,
        draft: StallRegionDraft,
    ): String? = when {
        bounds.area > MAX_AREA -> "stall_region.area_limit"
        parentBoundsContain(polygon, parent, bounds, draft) -> null
        else -> "stall_region.outside_parent"
    }

    private fun validateVerticalBounds(draft: StallRegionDraft): String? {
        val floor = draft.floorY ?: return "stall_region.mark_floor_first"
        val top = draft.topY ?: return "stall_region.height_required"
        return if (top <= floor) "stall_region.bad_height" else null
    }

    private fun parentBoundsContain(
        polygon: ProtectedPolygonalRegion,
        parent: ProtectedRegion,
        bounds: FootprintBounds,
        draft: StallRegionDraft,
    ): Boolean {
        val bottomY = requireNotNull(draft.floorY) + 1
        val topY = requireNotNull(draft.topY)
        val check = ParentBoundsCheck(polygon, parent, bounds, bottomY, topY)
        return allColumnsInsideParent(check)
    }

    private fun validPoints(draft: StallRegionDraft): Boolean =
        draft.points.size >= MIN_POINTS && StallPolygonGeometry.valid(draft.points)

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

    private fun createPolygon(draft: StallRegionDraft): ProtectedPolygonalRegion? {
        val floor = draft.floorY ?: return null
        val top = draft.topY ?: return null
        return runCatching {
            ProtectedPolygonalRegion(draft.id, draft.points.map { BlockVector2.at(it.x, it.z) }, floor + 1, top)
        }.getOrNull()
    }

    private fun bounds(draft: StallRegionDraft): FootprintBounds = FootprintBounds(
        draft.points.minOf { it.x }, draft.points.maxOf { it.x }, draft.points.minOf { it.z }, draft.points.maxOf { it.z },
    )

    private fun allColumnsInsideParent(check: ParentBoundsCheck): Boolean =
        (check.bounds.minX..check.bounds.maxX).all { x -> (check.bounds.minZ..check.bounds.maxZ).all { z ->
        val bottom = BlockVector3.at(x, check.bottomY, z)
        !check.polygon.contains(bottom) || (check.parent.contains(bottom) && check.parent.contains(BlockVector3.at(x, check.topY, z)))
    } }

    private fun plan(draft: StallRegionDraft, manager: RegionManager): PlanResult {
        if (draft.floorY == null) return PlanResult(error = "stall_region.mark_floor_first")
        if (draft.topY == null) return PlanResult(error = "stall_region.height_required")
        val previous = manager.getRegion(draft.id)
        val replacement = createPolygon(draft) ?: return PlanResult(error = "stall_region.invalid_polygon")
        configureReplacement(replacement, previous, manager)?.let { return PlanResult(error = it) }
        return PlanResult(RegionPlan(manager, previous, replacement))
    }

    private fun configureReplacement(
        replacement: ProtectedPolygonalRegion,
        previous: ProtectedRegion?,
        manager: RegionManager,
    ): String? = if (previous != null) {
        replacement.copyFrom(previous)
        null
    } else {
        configureNewRegion(replacement, manager)
    }

    private fun configureNewRegion(replacement: ProtectedPolygonalRegion, manager: RegionManager): String? {
        val parent = manager.getRegion(config.market.parentRegion) ?: return "stall_region.parent_missing"
        replacement.priority = config.market.stallPriority
        return if (runCatching { replacement.parent = parent }.isFailure) "stall_region.parent_invalid" else null
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

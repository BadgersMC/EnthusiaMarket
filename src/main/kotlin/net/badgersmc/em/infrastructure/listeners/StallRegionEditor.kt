package net.badgersmc.em.infrastructure.listeners

import net.badgersmc.em.config.EnthusiaMarketConfig
import net.badgersmc.em.domain.ports.RegionProvisioner
import net.badgersmc.nexus.annotations.Component
import net.badgersmc.nexus.i18n.LangService
import org.bukkit.Bukkit
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockDamageEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.plugin.Plugin
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal data class StallRegionPoint(val x: Int, val z: Int)
internal data class StallRegionDraft(
    val world: String,
    val id: String,
    val points: MutableList<StallRegionPoint> = mutableListOf(),
    var floorY: Int? = null,
    var topY: Int? = null,
    var revision: Int = 0,
    var previewedRevision: Int? = null,
    var previewedAt: Instant? = null,
)

/** In-game polygon selection, private preview and explicit save flow (REQ-410). */
@Component
@net.badgersmc.nexus.paper.listeners.Listener
class StallRegionEditor(
    private val config: EnthusiaMarketConfig,
    provisioner: RegionProvisioner,
    private val lang: LangService,
    plugin: Plugin,
) : Listener {
    private val drafts = ConcurrentHashMap<UUID, StallRegionDraft>()
    private val writer = StallRegionWorldGuardWriter(config, provisioner)
    private val renderer = StallRegionPreviewRenderer(plugin)

    fun active(player: Player): Boolean = drafts.containsKey(player.uniqueId)

    fun begin(player: Player, id: String): String {
        if (!id.matches(Regex("${Regex.escape(config.market.regionPrefix)}[0-9]+"))) return "stall_region.bad_id"
        if (Bukkit.getWorld(config.market.world) == null) return "stall_region.world_missing"
        renderer.stop(player)
        drafts[player.uniqueId] = StallRegionDraft(config.market.world, id)
        return "stall_region.started"
    }

    fun addPoint(player: Player, block: Block): String {
        val draft = drafts[player.uniqueId] ?: return "stall_region.not_editing"
        val problem = pointError(draft, block) ?: pointShapeError(draft, StallRegionPoint(block.x, block.z))
        if (problem != null) return problem
        draft.floorY = draft.floorY ?: block.y
        draft.points += StallRegionPoint(block.x, block.z)
        invalidate(draft)
        player.sendMessage(lang.msg("stall_region.point_added", "count" to draft.points.size, "x" to block.x, "y" to block.y, "z" to block.z))
        renderer.render(player, draft)
        return ""
    }

    private fun pointError(draft: StallRegionDraft, block: Block): String? = when {
        block.world.name != draft.world -> "stall_region.wrong_world"
        !block.type.isSolid || !block.getRelative(0, 1, 0).isPassable -> "stall_region.floor_only"
        draft.floorY != null && draft.floorY != block.y -> "stall_region.floor_mismatch"
        else -> null
    }

    private fun pointShapeError(draft: StallRegionDraft, point: StallRegionPoint): String? {
        if (point in draft.points) return "stall_region.duplicate_point"
        if (draft.points.size >= MAX_POINTS) return "stall_region.point_limit"
        val first = draft.points.firstOrNull()
        if (first != null && (kotlin.math.abs(point.x - first.x) > MAX_SPAN || kotlin.math.abs(point.z - first.z) > MAX_SPAN)) {
            return "stall_region.span_limit"
        }
        if (draft.points.size >= 2 && !StallPolygonGeometry.valid(draft.points + point)) return "stall_region.invalid_polygon"
        return null
    }

    fun undo(player: Player): String {
        val draft = drafts[player.uniqueId] ?: return "stall_region.not_editing"
        if (draft.points.isEmpty()) return "stall_region.no_points"
        draft.points.removeAt(draft.points.lastIndex)
        if (draft.points.isEmpty()) draft.floorY = null
        invalidate(draft)
        renderer.render(player, draft)
        return "stall_region.point_removed"
    }

    fun setTop(player: Player, y: Int): String {
        val draft = drafts[player.uniqueId] ?: return "stall_region.not_editing"
        val world = Bukkit.getWorld(draft.world) ?: return "stall_region.world_missing"
        val floor = draft.floorY ?: return "stall_region.mark_floor_first"
        if (y <= floor || y >= world.maxHeight || y < world.minHeight) return "stall_region.bad_height"
        if (draft.points.size < 3 || !StallPolygonGeometry.valid(draft.points)) return "stall_region.need_polygon"
        draft.topY = y
        invalidate(draft)
        renderer.render(player, draft)
        return "stall_region.height_set"
    }

    fun preview(player: Player): String {
        val draft = drafts[player.uniqueId] ?: return "stall_region.not_editing"
        writer.validate(draft)?.let { return it }
        draft.previewedRevision = draft.revision
        draft.previewedAt = Instant.now()
        renderer.render(player, draft, repeated = true)
        return "stall_region.previewed"
    }

    fun confirm(player: Player): String {
        val draft = drafts[player.uniqueId] ?: return "stall_region.not_editing"
        if (!hasFreshPreview(draft)) return "stall_region.preview_required"
        writer.validate(draft)?.let { return it }
        val result = writer.save(draft)
        if (result != "stall_region.saved") return result
        drafts.remove(player.uniqueId)
        renderer.stop(player)
        return result
    }

    private fun hasFreshPreview(draft: StallRegionDraft): Boolean {
        val previewAt = draft.previewedAt ?: return false
        return draft.previewedRevision == draft.revision && !Instant.now().isAfter(previewAt.plusSeconds(PREVIEW_TTL_SECONDS))
    }

    fun cancel(player: Player): String {
        if (drafts.remove(player.uniqueId) == null) return "stall_region.not_editing"
        renderer.stop(player)
        return "stall_region.cancelled"
    }

    @EventHandler
    fun onSelect(event: PlayerInteractEvent) {
        val player = event.player
        if (event.hand != EquipmentSlot.HAND || !active(player) || !player.hasPermission(REGION_PERMISSION)) return
        when (event.action) {
            Action.LEFT_CLICK_BLOCK -> {
                event.isCancelled = true
                event.clickedBlock?.let { addPoint(player, it) }?.takeIf(String::isNotEmpty)?.let { player.sendMessage(lang.msg(it)) }
            }
            Action.RIGHT_CLICK_BLOCK -> {
                event.isCancelled = true
                player.sendMessage(lang.msg(undo(player)))
            }
            else -> Unit
        }
    }

    @EventHandler fun onBreak(event: BlockBreakEvent) { cancelInEditingMode(event.player, event) }
    @EventHandler fun onDamage(event: BlockDamageEvent) { cancelInEditingMode(event.player, event) }
    @EventHandler fun onQuit(event: PlayerQuitEvent) { drafts.remove(event.player.uniqueId); renderer.stop(event.player) }
    @EventHandler fun onWorldChange(event: PlayerChangedWorldEvent) { drafts.remove(event.player.uniqueId); renderer.stop(event.player) }

    private fun cancelInEditingMode(player: Player, event: org.bukkit.event.Cancellable) {
        if (active(player) && player.hasPermission(REGION_PERMISSION)) event.isCancelled = true
    }

    private fun invalidate(draft: StallRegionDraft) { draft.revision++; draft.previewedRevision = null; draft.previewedAt = null }

    companion object {
        private const val MAX_POINTS = 64
        private const val MAX_SPAN = 128
        private const val PREVIEW_TTL_SECONDS = 30L
        private const val REGION_PERMISSION = "enthusiamarket.admin.region"
    }
}

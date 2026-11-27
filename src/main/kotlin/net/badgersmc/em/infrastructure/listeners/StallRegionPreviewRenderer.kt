package net.badgersmc.em.infrastructure.listeners

import org.bukkit.Bukkit
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.max

/** Draws a private, particle-only preview of the chosen floor prism. */
internal class StallRegionPreviewRenderer(private val plugin: Plugin) {
    private data class EdgeContext(
        val player: Player,
        val draft: StallRegionDraft,
        val levels: List<Double>,
        val bottom: Double,
        val verticalHeight: Int,
        val stride: Int,
    )
    private data class HorizontalEdge(val start: StallRegionPoint, val end: StallRegionPoint)
    private val tasks = mutableMapOf<UUID, org.bukkit.scheduler.BukkitTask>()

    fun stop(player: Player) { tasks.remove(player.uniqueId)?.cancel() }

    fun render(player: Player, draft: StallRegionDraft, repeated: Boolean = false) {
        if (!canRender(draft)) return
        tasks.remove(player.uniqueId)?.cancel()
        val task = startRenderTask(player, draft)
        tasks[player.uniqueId] = task
        scheduleExpiry(player, task, repeated)
    }

    private fun canRender(draft: StallRegionDraft): Boolean = Bukkit.getWorld(draft.world) != null && draft.points.isNotEmpty()

    private fun startRenderTask(player: Player, draft: StallRegionDraft) = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
        if (player.isOnline) draw(player, draft)
    }, 0L, 10L)

    private fun scheduleExpiry(player: Player, task: org.bukkit.scheduler.BukkitTask, repeated: Boolean) {
        val duration = if (repeated) PREVIEW_SECONDS * 20 else 1L
        plugin.server.scheduler.runTaskLater(plugin, Runnable { cancelIfCurrent(player, task) }, duration)
    }

    private fun cancelIfCurrent(player: Player, task: org.bukkit.scheduler.BukkitTask) {
        if (tasks[player.uniqueId] === task) tasks.remove(player.uniqueId)?.cancel()
    }

    private fun draw(player: Player, draft: StallRegionDraft) {
        val floor = draft.floorY ?: return
        val bottom = floor + 1.5
        val top = draft.topY?.plus(0.5)
        val levels = listOfNotNull(bottom, top)
        val verticalHeight = draft.topY?.minus(floor)?.minus(1) ?: 0
        val particleCount = edgeSamples(draft) * levels.size + draft.points.size * (verticalHeight + 1)
        val stride = max(1, ceil(particleCount.toDouble() / PARTICLE_BUDGET).toInt())
        val context = EdgeContext(player, draft, levels, bottom, verticalHeight, stride)
        draft.points.indices.forEach { i -> renderCornerEdges(context, i) }
    }

    private fun renderCornerEdges(context: EdgeContext, index: Int) {
        val (player, draft, levels, bottom, verticalHeight, stride) = context
        val point = draft.points[index]
        val next = draft.points[(index + 1) % draft.points.size]
        levels.forEach { renderHorizontalEdge(context, HorizontalEdge(point, next), it) }
        if (draft.topY != null) renderVerticalEdge(context, point)
    }

    private fun edgeSamples(draft: StallRegionDraft): Int = draft.points.indices.sumOf { i ->
        val a = draft.points[i]; val b = draft.points[(i + 1) % draft.points.size]
        max(kotlin.math.abs(a.x - b.x), kotlin.math.abs(a.z - b.z))
    }

    private fun renderHorizontalEdge(context: EdgeContext, edge: HorizontalEdge, y: Double) {
        val player = context.player
        val stride = context.stride
        val (a, b) = edge
        val steps = max(1, max(kotlin.math.abs(a.x - b.x), kotlin.math.abs(a.z - b.z)))
        for (step in 0..steps step stride) {
            val fraction = step.toDouble() / steps
            player.spawnParticle(Particle.END_ROD, a.x + (b.x - a.x) * fraction + 0.5, y,
                a.z + (b.z - a.z) * fraction + 0.5, 1, 0.0, 0.0, 0.0, 0.0)
        }
    }

    private fun renderVerticalEdge(context: EdgeContext, point: StallRegionPoint) {
        val (player, _, _, bottom, height, stride) = context
        for (step in 0..height step stride) player.spawnParticle(Particle.END_ROD,
            point.x + 0.5, bottom + step, point.z + 0.5, 1, 0.0, 0.0, 0.0, 0.0)
    }

    companion object {
        private const val PARTICLE_BUDGET = 1_200
        private const val PREVIEW_SECONDS = 30L
    }
}

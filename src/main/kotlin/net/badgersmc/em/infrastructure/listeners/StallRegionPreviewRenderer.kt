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
    private val tasks = mutableMapOf<UUID, org.bukkit.scheduler.BukkitTask>()

    fun stop(player: Player) { tasks.remove(player.uniqueId)?.cancel() }

    fun render(player: Player, draft: StallRegionDraft, repeated: Boolean = false) {
        if (Bukkit.getWorld(draft.world) == null || draft.points.isEmpty()) return
        tasks.remove(player.uniqueId)?.cancel()
        val task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (!player.isOnline) return@Runnable
            draw(player, draft)
        }, 0L, 10L)
        tasks[player.uniqueId] = task
        val duration = if (repeated) PREVIEW_SECONDS * 20 else 1L
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (tasks[player.uniqueId] === task) tasks.remove(player.uniqueId)?.cancel()
        }, duration)
    }

    private fun draw(player: Player, draft: StallRegionDraft) {
        val floor = draft.floorY ?: return
        val bottom = floor + 1.5
        val top = draft.topY?.plus(0.5)
        val levels = listOfNotNull(bottom, top)
        val verticalHeight = draft.topY?.minus(floor)?.minus(1) ?: 0
        val particleCount = edgeSamples(draft) * levels.size + draft.points.size * (verticalHeight + 1)
        val stride = max(1, ceil(particleCount.toDouble() / PARTICLE_BUDGET).toInt())
        draft.points.indices.forEach { i -> renderCornerEdges(player, draft, i, levels, bottom, verticalHeight, stride) }
    }

    private fun renderCornerEdges(
        player: Player, draft: StallRegionDraft, index: Int, levels: List<Double>, bottom: Double,
        verticalHeight: Int, stride: Int,
    ) {
        val point = draft.points[index]
        val next = draft.points[(index + 1) % draft.points.size]
        levels.forEach { renderHorizontalEdge(player, point, next, it, stride) }
        if (draft.topY != null) renderVerticalEdge(player, point, bottom, verticalHeight, stride)
    }

    private fun edgeSamples(draft: StallRegionDraft): Int = draft.points.indices.sumOf { i ->
        val a = draft.points[i]; val b = draft.points[(i + 1) % draft.points.size]
        max(kotlin.math.abs(a.x - b.x), kotlin.math.abs(a.z - b.z))
    }

    private fun renderHorizontalEdge(player: Player, a: StallRegionPoint, b: StallRegionPoint, y: Double, stride: Int) {
        val steps = max(1, max(kotlin.math.abs(a.x - b.x), kotlin.math.abs(a.z - b.z)))
        for (step in 0..steps step stride) {
            val fraction = step.toDouble() / steps
            player.spawnParticle(Particle.END_ROD, a.x + (b.x - a.x) * fraction + 0.5, y,
                a.z + (b.z - a.z) * fraction + 0.5, 1, 0.0, 0.0, 0.0, 0.0)
        }
    }

    private fun renderVerticalEdge(player: Player, point: StallRegionPoint, bottom: Double, height: Int, stride: Int) {
        for (step in 0..height step stride) player.spawnParticle(Particle.END_ROD,
            point.x + 0.5, bottom + step, point.z + 0.5, 1, 0.0, 0.0, 0.0, 0.0)
    }

    companion object {
        private const val PARTICLE_BUDGET = 1_200
        private const val PREVIEW_SECONDS = 30L
    }
}

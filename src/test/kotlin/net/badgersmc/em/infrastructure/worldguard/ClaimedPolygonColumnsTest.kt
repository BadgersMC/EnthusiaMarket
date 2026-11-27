package net.badgersmc.em.infrastructure.worldguard

import com.sk89q.worldedit.math.BlockVector2
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion
import net.badgersmc.em.domain.ports.RegionProvider.RegionBounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClaimedPolygonColumnsTest {
    private fun outline(vararg coords: Pair<Int, Int>) = coords.map { BlockVector2.at(it.first, it.second) }

    @Test fun `six by six L excludes its two by two aisle on every layer`() {
        val points = outline(0 to 0, 5 to 0, 5 to 3, 3 to 3, 3 to 5, 0 to 5)
        val region = ProtectedPolygonalRegion("stall69", points, 100, 103)
        val columns = claimedPolygonColumns(points)
        assertEquals(32L, columns)
        assertTrue(region.contains(BlockVector3.at(3, 100, 5)))
        assertFalse(region.contains(BlockVector3.at(4, 100, 5)))
        assertEquals(128L, RegionBounds(0, 100, 0, 5, 103, 5, columns).claimedVolume)
        assertEquals(columns, claimedPolygonColumns(points.reversed()))
    }

    @Test fun `diagonal boundary count matches WorldGuard membership`() {
        val points = outline(-8 to -3, -2 to -6, 1 to 2, -4 to 0, -6 to 4)
        val region = ProtectedPolygonalRegion("diagonal", points, 10, 12)
        val columns = (-8..1).sumOf { x -> (-6..4).count { z -> region.contains(BlockVector3.at(x, 10, z)) } }
        assertEquals(columns.toLong(), claimedPolygonColumns(points))
    }

    @Test fun `crossing boundary has unavailable volume`() {
        assertNull(claimedPolygonColumns(outline(0 to 0, 5 to 5, 0 to 6, 5 to 0)))
    }
}

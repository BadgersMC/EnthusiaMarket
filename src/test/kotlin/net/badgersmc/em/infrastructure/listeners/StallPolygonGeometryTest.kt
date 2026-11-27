package net.badgersmc.em.infrastructure.listeners

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StallPolygonGeometryTest {
    @Test fun `validates convex and concave footprints`() {
        assertTrue(StallPolygonGeometry.valid(listOf(
            StallRegionPoint(0, 0), StallRegionPoint(8, 0), StallRegionPoint(8, 6), StallRegionPoint(0, 6),
        )))
        assertTrue(StallPolygonGeometry.valid(listOf(
            StallRegionPoint(0, 0), StallRegionPoint(8, 0), StallRegionPoint(8, 8),
            StallRegionPoint(4, 4), StallRegionPoint(0, 8),
        )))
    }

    @Test fun `rejects crossed collapsed duplicate and undersized outlines`() {
        assertFalse(StallPolygonGeometry.valid(listOf(
            StallRegionPoint(0, 0), StallRegionPoint(8, 8), StallRegionPoint(0, 8), StallRegionPoint(8, 0),
        )))
        assertFalse(StallPolygonGeometry.valid(listOf(
            StallRegionPoint(0, 0), StallRegionPoint(4, 0), StallRegionPoint(8, 0),
        )))
        assertFalse(StallPolygonGeometry.valid(listOf(
            StallRegionPoint(0, 0), StallRegionPoint(8, 0), StallRegionPoint(8, 8), StallRegionPoint(0, 0),
        )))
        assertFalse(StallPolygonGeometry.valid(listOf(StallRegionPoint(0, 0), StallRegionPoint(1, 1))))
    }
}

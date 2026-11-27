package net.badgersmc.em.infrastructure.listeners

import java.math.BigInteger

/** Validates simple integer polygons without floating-point overflow. */
internal object StallPolygonGeometry {
    private data class Orientations(val first: Int, val second: Int, val third: Int, val fourth: Int)

    fun valid(points: List<StallRegionPoint>): Boolean {
        if (points.size !in MIN_POINTS..MAX_POINTS || points.distinct().size != points.size) return false
        if (signedArea(points) == BigInteger.ZERO) return false
        if (hasOverlappingAdjacentEdges(points)) return false
        return !hasNonAdjacentIntersection(points)
    }

    private fun signedArea(points: List<StallRegionPoint>): BigInteger = points.indices.fold(BigInteger.ZERO) { sum, i ->
        val a = points[i]; val b = points[(i + 1) % points.size]
        sum + product(a.x, b.z) - product(b.x, a.z)
    }

    private fun product(a: Int, b: Int): BigInteger = BigInteger.valueOf(a.toLong()).multiply(BigInteger.valueOf(b.toLong()))

    private fun hasOverlappingAdjacentEdges(points: List<StallRegionPoint>): Boolean = points.indices.any { i ->
        val previous = points[(i - 1 + points.size) % points.size]
        val current = points[i]
        val next = points[(i + 1) % points.size]
        orientation(previous, current, next) == 0 && !onSegment(previous, current, next)
    }

    private fun hasNonAdjacentIntersection(points: List<StallRegionPoint>): Boolean = points.indices.any { i ->
        (i + 1 until points.size).any { j ->
            if (j == i + 1 || (i == 0 && j == points.lastIndex)) false
            else intersects(points[i], points[(i + 1) % points.size], points[j], points[(j + 1) % points.size])
        }
    }

    private fun intersects(a: StallRegionPoint, b: StallRegionPoint, c: StallRegionPoint, d: StallRegionPoint): Boolean {
        val orientations = Orientations(orientation(a, b, c), orientation(a, b, d), orientation(c, d, a), orientation(c, d, b))
        return hasProperCrossing(orientations) || hasCollinearIntersection(a, b, c, d, orientations)
    }

    private fun hasProperCrossing(orientations: Orientations): Boolean =
        oppositeSigns(orientations.first, orientations.second) && oppositeSigns(orientations.third, orientations.fourth)

    private fun oppositeSigns(a: Int, b: Int): Boolean = (a > 0 && b < 0) || (a < 0 && b > 0)

    private fun hasCollinearIntersection(
        a: StallRegionPoint, b: StallRegionPoint, c: StallRegionPoint, d: StallRegionPoint,
        orientations: Orientations,
    ): Boolean = (orientations.first == 0 && onSegment(a, c, b)) ||
        (orientations.second == 0 && onSegment(a, d, b)) ||
        (orientations.third == 0 && onSegment(c, a, d)) || (orientations.fourth == 0 && onSegment(c, b, d))

    private fun orientation(p: StallRegionPoint, q: StallRegionPoint, r: StallRegionPoint): Int {
        val first = BigInteger.valueOf(q.x.toLong() - p.x).multiply(BigInteger.valueOf(r.z.toLong() - p.z))
        val second = BigInteger.valueOf(q.z.toLong() - p.z).multiply(BigInteger.valueOf(r.x.toLong() - p.x))
        return first.subtract(second).signum()
    }

    private fun onSegment(a: StallRegionPoint, point: StallRegionPoint, b: StallRegionPoint): Boolean =
        point.x in minOf(a.x, b.x)..maxOf(a.x, b.x) && point.z in minOf(a.z, b.z)..maxOf(a.z, b.z)

    private const val MIN_POINTS = 3
    private const val MAX_POINTS = 64
}

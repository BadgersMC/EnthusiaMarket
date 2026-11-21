package net.badgersmc.em.infrastructure.worldguard

import com.sk89q.worldedit.math.BlockVector2
import java.awt.geom.Line2D
import java.math.BigInteger

/** Pick's theorem counts interior and boundary block centers without scanning the enclosing box. */
internal fun claimedPolygonColumns(vertices: List<BlockVector2>): Long? {
    val points = vertices.filterIndexed { index, point -> point != vertices[(index + vertices.size - 1) % vertices.size] }
    if (points.size < 3) return null
    var twiceArea = BigInteger.ZERO
    var boundary = BigInteger.ZERO
    for (i in points.indices) {
        val a = points[i]
        val next = (i + 1) % points.size
        val b = points[next]
        // An intersecting outline has no single simple interior for this formula.
        for (j in i + 2 until points.size) {
            val end = (j + 1) % points.size
            if (end == i) continue
            val c = points[j]
            val d = points[end]
            if (Line2D.linesIntersect(a.x().toDouble(), a.z().toDouble(), b.x().toDouble(), b.z().toDouble(),
                    c.x().toDouble(), c.z().toDouble(), d.x().toDouble(), d.z().toDouble())) return null
        }
        val ax = a.x().toBigInteger()
        val az = a.z().toBigInteger()
        val bx = b.x().toBigInteger()
        val bz = b.z().toBigInteger()
        twiceArea += ax * bz - bx * az
        boundary += (bx - ax).gcd(bz - az)
    }
    if (twiceArea.signum() == 0) return null
    val columns = (twiceArea.abs() + boundary).shiftRight(1) + BigInteger.ONE
    return if (columns.bitLength() <= 63) columns.toLong() else null
}

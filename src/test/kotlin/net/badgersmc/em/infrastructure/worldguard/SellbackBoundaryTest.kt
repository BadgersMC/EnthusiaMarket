package net.badgersmc.em.infrastructure.worldguard

import com.sk89q.worldedit.extent.Extent
import com.sk89q.worldedit.math.BlockVector2
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.regions.CuboidRegion
import com.sk89q.worldedit.world.block.BaseBlock
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SellbackBoundaryTest {
    @Test fun `legacy box restores interior while excluding the frame support in the notch`() {
        val outline = listOf(0 to 0, 5 to 0, 5 to 3, 3 to 3, 3 to 5, 0 to 5)
        val boundary = ProtectedPolygonalRegion("stall", outline.map { BlockVector2.at(it.first, it.second) }, 100, 102)
        val saved = CuboidRegion(BlockVector3.at(0, 99, 0), BlockVector3.at(5, 103, 5))
        val source = mockk<Extent>()
        val target = mockk<Extent>()
        val block = mockk<BaseBlock>()
        val writes = mutableListOf<BlockVector3>()
        every { source.getFullBlock(any()) } returns block
        every { target.setBlock(any(), block) } answers {
            val pos = firstArg<BlockVector3>()
            writes.add(BlockVector3.at(pos.x(), pos.y(), pos.z()))
            true
        }

        restoreStallBlocks(source, saved, target, boundary)

        val expected = buildSet {
            for (x in 0..5) for (y in 100..102) for (z in 0..5) {
                if (x <= 3 || z <= 3) add(BlockVector3.at(x, y, z))
            }
        }
        assertEquals(expected, writes.toSet())
        assertFalse(BlockVector3.at(4, 101, 4) in writes)
        verify(exactly = 0) { source.getFullBlock(BlockVector3.at(4, 101, 4)) }
    }
}

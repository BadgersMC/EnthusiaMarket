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
    @Test fun `local FAWE clipboard is anchored at the stall minimum and clips the frame notch`() {
        val outline = listOf(0 to 0, 5 to 0, 5 to 3, 3 to 3, 3 to 5, 0 to 5)
        val boundary = ProtectedPolygonalRegion("trial",
            outline.map { BlockVector2.at(512 + it.first, 512 + it.second) }, 296, 297)
        val saved = CuboidRegion(BlockVector3.ZERO, BlockVector3.at(5, 1, 5))
        val source = mockk<Extent>()
        val target = mockk<Extent>()
        val marker = mockk<BaseBlock>()
        val other = mockk<BaseBlock>()
        every { source.getFullBlock(any()) } returns other
        every { source.getFullBlock(BlockVector3.at(1, 0, 1)) } returns marker
        val writes = mutableMapOf<BlockVector3, BaseBlock>()
        every { target.setBlock(any(), any<BaseBlock>()) } answers {
            val position = firstArg<BlockVector3>()
            writes[BlockVector3.at(position.x(), position.y(), position.z())] = secondArg()
            true
        }
        restoreStallBlocks(source, saved, target, boundary)
        assertEquals(marker, writes[BlockVector3.at(513, 296, 513)])
        assertFalse(BlockVector3.at(516, 296, 516) in writes)
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            restoreStallBlocks(source, CuboidRegion(BlockVector3.ZERO, BlockVector3.at(7, 5, 7)), target, boundary)
        }
    }
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

package net.badgersmc.em.infrastructure.bootstrap

import io.mockk.mockk
import net.badgersmc.em.application.StallAccessIndex
import net.badgersmc.em.application.StallAccessSettingsService
import net.badgersmc.em.domain.ports.StallAccessPolicy
import net.badgersmc.em.domain.stall.StallRepository
import net.badgersmc.nexus.core.NexusContext
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class StallAccessBeansTest {
    @Test
    fun `startup wiring resolves index and repository to one singleton`() {
        val context = NexusContext.create()
        try {
            val index = StallAccessIndex(mockk(relaxed = true))
            StallAccessBeans.registerIndex(context, index)
            assertSame(index, context.getBean(StallAccessIndex::class))
            assertSame(index, context.getBean(StallRepository::class))
        } finally {
            context.close()
        }
    }

    @Test
    fun `startup wiring resolves settings and policy to one singleton`() {
        val context = NexusContext.create()
        try {
            val index = StallAccessIndex(mockk(relaxed = true))
            val settings = StallAccessSettingsService(index, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
            StallAccessBeans.registerSettings(context, settings)
            assertSame(settings, context.getBean(StallAccessSettingsService::class))
            assertSame(settings, context.getBean(StallAccessPolicy::class))
        } finally {
            context.close()
        }
    }
}

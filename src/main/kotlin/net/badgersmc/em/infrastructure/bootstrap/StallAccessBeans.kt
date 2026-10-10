package net.badgersmc.em.infrastructure.bootstrap

import net.badgersmc.em.application.StallAccessIndex
import net.badgersmc.em.application.StallAccessSettingsService
import net.badgersmc.nexus.core.NexusContext

/** Nexus indexes concrete registrations by their interfaces; aliases create ambiguous beans. */
internal object StallAccessBeans {
    fun registerIndex(context: NexusContext, index: StallAccessIndex) {
        context.registerBean("stallRepository", StallAccessIndex::class, index)
    }

    fun registerSettings(context: NexusContext, settings: StallAccessSettingsService) {
        context.registerBean("stallAccessSettings", StallAccessSettingsService::class, settings)
    }
}

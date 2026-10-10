@file:Suppress("InvalidPackageDeclaration")
package net.badgersmc.em.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class StartupDependencyMetadataTest {
    @Test fun `required startup Guilds classpath does not introduce a Staff Market Guilds order cycle`() {
        val descriptor = Files.readString(Path.of("src/main/resources/paper-plugin.yml"))
        val guilds = descriptor.substringAfter("    LumaGuilds:").substringBefore("    WorldGuard:")
        assertTrue(guilds.contains("load: OMIT"), "Guilds already enables at STARTUP; BEFORE closes the Staff/Market/Guilds cycle")
        assertTrue(guilds.contains("required: true"), "Market must still require its Guilds runtime")
        assertTrue(guilds.contains("join-classpath: true"), "Removing ordering must retain companion API class access")
    }
}

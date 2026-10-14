package net.badgersmc.em.infrastructure.guild

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.RegisteredServiceProvider
import org.bukkit.plugin.ServicePriority
import org.bukkit.plugin.ServicesManager
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.assertEquals

class GuildShopXpRuntimeContractTest {
    @Test fun `real companion interface works through isolated plugin classloader using JDK values`() {
        val path = checkNotNull(System.getenv("LUMAGUILDS_JAR")) {
            "LUMAGUILDS_JAR is required; the paired-runtime release contract may not be skipped"
        }
        check(Files.isRegularFile(Path.of(path))) { "LUMAGUILDS_JAR does not name a real file: $path" }
        URLClassLoader(arrayOf(Path.of(path).toUri().toURL()), ClassLoader.getPlatformClassLoader()).use { loader ->
            @Suppress("UNCHECKED_CAST")
            val api = Class.forName("net.lumalyte.lg.api.GuildShopXpApi", true, loader) as Class<Any>
            val calls = mutableListOf<String>()
            val provider = Proxy.newProxyInstance(loader, arrayOf(api)) { _, method, args ->
                calls.add(method.name)
                when (method.name) {
                    "apiVersion" -> 2
                    "prepare" -> {
                        assertEquals(4, args.size)
                        assertEquals("java.util.UUID", args[0].javaClass.name)
                        "PREPARED"
                    }
                    "abort" -> "ABORTED"
                    "complete" -> "AWARDED:5"
                    else -> error("Unexpected API call")
                }
            }
            val plugin = mockk<Plugin>()
            every { plugin.isEnabled } returns true
            val manager = mockk<ServicesManager>()
            every { manager.knownServices } returns listOf(api)
            every { manager.getRegistration(api) } returns RegisteredServiceProvider(api, provider, ServicePriority.Normal, plugin)
            mockkStatic(Bukkit::class)
            try {
                every { Bukkit.getServicesManager() } returns manager
                val gateway = BukkitGuildShopXpGateway()
                val id = UUID.randomUUID()
                assertEquals("PREPARED", gateway.prepare(id, UUID.randomUUID(), UUID.randomUUID(), 1000))
                assertEquals("ABORTED", gateway.abort(id))
                assertEquals("AWARDED:5", gateway.complete(id))
                assertEquals(
                    listOf("apiVersion", "prepare", "apiVersion", "abort", "apiVersion", "complete"),
                    calls,
                )
            } finally {
                unmockkAll()
            }
        }
    }
}

package net.badgersmc.em.infrastructure.i18n

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BalancedLocaleUpgradeTest {
    @TempDir lateinit var folder: Path

    @Test fun `refreshes stock strings and preserves custom wording on repeated startup`() {
        val target = Files.createDirectories(folder.resolve("lang")).resolve("en_US.yml")
        val loader = EnthusiaMarketLang::class.java.classLoader
        requireNotNull(loader.getResourceAsStream("migrations/market-text/en_US-before-balanced.yml")).use {
            Files.copy(it, target)
        }
        val old = YamlConfiguration.loadConfiguration(target.toFile())
        old.set("admin.list.empty", "Our own directory message")
        old.save(target.toFile())
        BalancedLocaleUpgrade.apply(folder, Logger.getAnonymousLogger())
        val result = YamlConfiguration.loadConfiguration(target.toFile())
        assertEquals("Our own directory message", result.getString("admin.list.empty"))
        assertTrue(result.getString("prefix").orEmpty().contains("#E4AB65:#F2B66D"))
        val text = Files.readString(target)
        assertFalse(text.any { it in "!✓✔✅☑" })
        BalancedLocaleUpgrade.apply(folder, Logger.getAnonymousLogger())
        assertEquals(text, Files.readString(target))
    }
}

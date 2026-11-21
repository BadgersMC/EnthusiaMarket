package net.badgersmc.em.infrastructure.i18n

import org.bukkit.configuration.file.YamlConfiguration
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.logging.Level
import java.util.logging.Logger

/** Nexus overlays the on-disk locale on the bundle. Refresh only unchanged historical defaults. */
internal object BalancedLocaleUpgrade {
    fun apply(dataFolder: Path, logger: Logger) {
        val target = dataFolder.resolve("lang/en_US.yml")
        if (!Files.isRegularFile(target)) return
        try {
            val current = YamlConfiguration().apply { load(target.toFile()) }
            val before = resource("migrations/market-text/en_US-before-balanced.yml")
            val after = resource("lang/en_US.yml")
            val replacements = before.getKeys(true).filter { key ->
                before.isString(key) && after.isString(key) &&
                    current.get(key) == before.get(key) && current.get(key) != after.get(key)
            }
            if (replacements.isEmpty()) return
            replacements.forEach { current.set(it, after.get(it)) }
            val temp = Files.createTempFile(target.parent, "market-locale-", ".tmp")
            try {
                current.save(temp.toFile())
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally {
                Files.deleteIfExists(temp)
            }
        } catch (failure: Exception) {
            logger.log(Level.WARNING, "Could not update the existing Market locale to the Balanced palette", failure)
        }
    }

    private fun resource(path: String): YamlConfiguration {
        val stream = requireNotNull(EnthusiaMarketLang::class.java.classLoader.getResourceAsStream(path))
        return stream.bufferedReader(Charsets.UTF_8).use { reader -> YamlConfiguration().apply { load(reader) } }
    }
}

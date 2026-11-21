package net.badgersmc.em.infrastructure.website

import java.io.File
import java.time.Instant
import javax.sql.DataSource
import net.badgersmc.em.config.EnthusiaMarketConfig
import net.badgersmc.em.infrastructure.persistence.WebsiteSalesArchiveSql
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin

internal class WebsiteSalesBootstrap(private val plugin: JavaPlugin, private val historyDrained: () -> Boolean) {
    fun start(ds: DataSource, config: EnthusiaMarketConfig): WebsiteSalesHttpServer? {
        try {
            val file = File(plugin.dataFolder, "website-sales.yml")
            if (!file.exists()) plugin.saveResource("website-sales.yml", false)
            val settings = YamlConfiguration.loadConfiguration(file)
            if (!settings.getBoolean("enabled", false)) return null
            require(salesCurrencyVerified())
            val server = websiteServer(ds, config, settings)
            try { server.start(); return server } catch (failure: Exception) { server.close(); throw failure }
        } catch (_: Exception) { plugin.logger.warning("Website sales feed disabled: invalid configuration or unavailable listener"); return null }
    }

    private fun websiteServer(ds: DataSource, config: EnthusiaMarketConfig, settings: YamlConfiguration): WebsiteSalesHttpServer {
        val generation = salesGeneration(settings)
        val configuredGeneration = generation.read() // Fail closed if control state is unavailable.
        val archive = WebsiteSalesArchiveSql(ds)
        return WebsiteSalesHttpServer(
            settings.getInt("port", 8124), websiteToken(settings),
            generation::read,
            settings.getString("bind-address", "127.0.0.1") ?: "127.0.0.1",
            reader = archive,
            coverage = {
                coverageWindow(config, settings, archive) {
                    settings.getString("award-generation") == configuredGeneration.toString() &&
                        generation.read() == configuredGeneration
                }
            },
        )
    }

    private fun websiteToken(settings: YamlConfiguration): String {
        return System.getenv("ENTHUSIA_MARKET_SALES_TOKEN")
            ?: settings.getString("token-file")?.takeIf(String::isNotBlank)?.let { File(plugin.dataFolder, it).readText().trim() }
            ?: ""
    }

    private fun salesGeneration(settings: YamlConfiguration): SalesGenerationFile {
        val generationPath = (System.getenv("ENTHUSIA_MARKET_SALES_GENERATION_FILE")
            ?: settings.getString("generation-file"))
            ?.takeIf { it.isNotBlank() } ?: error("Missing sales generation control file")
        return SalesGenerationFile(java.nio.file.Path.of(generationPath))
    }

    private fun coverageWindow(
        config: EnthusiaMarketConfig,
        settings: YamlConfiguration,
        archive: WebsiteSalesArchiveSql,
        generationMatches: () -> Boolean,
    ): Pair<Instant, Instant>? {
        val through = Instant.now()
        if (!config.shop.archiveCaptureEnabled) return null
        val start = coverageStart(settings, through) ?: return null
        if (!generationMatches() || !historyDrained()) return null
        return if (archive.hasRejectionsSince(start.toEpochMilli())) null else start to through
    }

    private fun coverageStart(settings: YamlConfiguration, through: Instant): Instant? =
        settings.getString("award-capture-from")?.takeIf { it.isNotBlank() }?.let(Instant::parse)?.takeIf { it < through }
}

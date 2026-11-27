package net.badgersmc.em

import org.bukkit.Bukkit
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.permissions.Permission
import org.bukkit.permissions.PermissionDefault

/** Registers permissions from the bundled descriptor without replacing server-managed nodes. */
internal object DeclaredPermissionRegistrar {
    fun register(plugin: JavaPlugin) {
        val permissions = load(plugin) ?: return
        val manager = Bukkit.getPluginManager()
        val count = permissions.getKeys(false).count { node -> registerNode(manager, permissions, node) }
        plugin.logger.info("Registered $count EnthusiaMarket permissions")
    }

    @Suppress("TooGenericExceptionCaught")
    private fun load(plugin: JavaPlugin): ConfigurationSection? {
        val stream = plugin::class.java.classLoader.getResourceAsStream("paper-plugin.yml") ?: run {
            plugin.logger.warning("paper-plugin.yml not found on classpath; skipping permission registration")
            return null
        }
        return try {
            val yaml = YamlConfiguration()
            yaml.options().pathSeparator('/')
            stream.use { yaml.load(it.reader(Charsets.UTF_8)) }
            yaml.getConfigurationSection("permissions")
        } catch (e: Exception) {
            plugin.logger.warning("Failed to read declared permissions: ${e.message}")
            null
        }
    }

    private fun registerNode(
        manager: org.bukkit.plugin.PluginManager,
        permissions: ConfigurationSection,
        node: String,
    ): Boolean {
        if (manager.getPermission(node) != null) return false
        return try {
            manager.addPermission(Permission(node, defaultOf(permissions.getString("$node/default"))))
            true
        } catch (_: IllegalArgumentException) {
            false // another plugin registered the node after the initial lookup
        }
    }

    private fun defaultOf(token: String?): PermissionDefault = when (token?.lowercase()) {
        "true" -> PermissionDefault.TRUE
        "false" -> PermissionDefault.FALSE
        "not op", "notop", "!op" -> PermissionDefault.NOT_OP
        else -> PermissionDefault.OP
    }
}

package net.badgersmc.em.interaction.bedrock

import net.badgersmc.em.application.StallBuyoutService
import net.badgersmc.em.domain.stall.StallId
import net.badgersmc.em.interaction.PurchaseFlow
import net.badgersmc.nexus.i18n.LangService
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.geysermc.cumulus.form.SimpleForm
import java.util.logging.Logger

/** Native Bedrock confirmation form; business rules remain in [StallBuyoutService]. */
class BedrockPurchaseConfirmForm(
    player: Player,
    private val stallId: StallId,
    private val price: Long,
    private val guild: PurchaseFlow.GuildOption?,
    private val buyout: StallBuyoutService,
    logger: Logger,
    lang: LangService,
) : BedrockMenuBase(player, logger, lang) {
    override fun buildForm(): SimpleForm = SimpleForm.builder()
        .title(lang.legacy("purchase_sign.msg.confirm_title"))
        .content(confirmContent())
        .button(lang.legacy("purchase_sign.msg.confirm_yes"))
        .button(lang.legacy("purchase_sign.msg.confirm_no"))
        .validResultHandler { response ->
            val button = response.clickedButtonId()
            if (Bukkit.isPrimaryThread()) handleButton(button)
            else Bukkit.getScheduler().runTask(JavaPlugin.getProvidingPlugin(javaClass), Runnable {
                if (player.isOnline) handleButton(button)
            })
        }
        .build()

    internal fun handleButton(button: Int) {
        if (button != 0) return
        val result = PurchaseFlow.execute(player, stallId, price, guild, buyout)
        player.sendMessage(PurchaseFlow.message(result, stallId, lang))
    }

    private fun confirmContent(): String {
        val key = if (guild != null) "purchase_sign.msg.confirm_guild" else "purchase_sign.msg.confirm_personal"
        val base = lang.legacy(key, "stall" to stallId.value, "price" to price)
        return if (guild != null) "$base\n${lang.legacy("purchase_sign.msg.method_guild_buyer", "guild_name" to guild.name)}" else base
    }
}

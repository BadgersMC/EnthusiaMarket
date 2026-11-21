package net.badgersmc.em.interaction.bedrock

import net.badgersmc.em.application.ShopManagementService
import net.badgersmc.em.interaction.ShopMenuPage
import net.badgersmc.em.domain.shop.Shop
import net.badgersmc.em.domain.shop.ShopRepository
import net.badgersmc.nexus.i18n.LangService
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.SimpleForm
import java.util.logging.Logger

class BedrockOwnedShopsForm(player: Player, private val shops: ShopRepository,
    private val management: ShopManagementService, logger: Logger, lang: LangService,
) : BedrockMenuBase(player, logger, lang) {
    private var pageIndex = 0

    override fun buildForm(): SimpleForm {
        val eligible = if (player.hasPermission("enthusiamarket.shop.use"))
            management.shopsOwnedBy(player.uniqueId).filter(::mayManage) else emptyList()
        val page = ShopMenuPage.of(eligible, pageIndex) { it.id }
        pageIndex = page.index
        val editable = page.items
        val builder = SimpleForm.builder().title(lang.legacy("gui.shop.owned.title"))
        builder.content(lang.legacy("gui.shop.search.page", "page" to page.index + 1,
            "pages" to page.pages, "count" to page.count))
        editable.forEach { builder.button("${it.stallId}: ${it.sellAmount}x / ${it.costAmount}") }
        val previous = if (page.index > 0) editable.size else -1
        val next = if (page.index + 1 < page.pages) editable.size + (if (previous >= 0) 1 else 0) else -1
        if (previous >= 0) builder.button(lang.legacy("gui.shop.search.prev"))
        if (next >= 0) builder.button(lang.legacy("gui.shop.search.next"))
        return builder.validResultHandler { response ->
            handleResponse(page, response.clickedButtonId(), previous, next)
        }.build()
    }

    private fun isNavigation(clicked: Int, previous: Int, next: Int): Boolean {
        return clicked >= 0 && (clicked == previous || clicked == next)
    }

    private fun handleResponse(page: ShopMenuPage<Shop>, clicked: Int, previous: Int, next: Int) {
        if (!player.hasPermission("enthusiamarket.shop.use")) return
        if (isNavigation(clicked, previous, next)) {
            navigate(page.index, clicked == previous)
            return
        }
        val selected = page.items.getOrNull(clicked) ?: return
        openEditable(selected)
    }

    private fun openEditable(selected: Shop) {
        val current = shops.findById(selected.id)
        if (current == null || !mayManage(current)) {
            open(player)
            return
        }
        BedrockShopEditForm(player, current, shops, logger, lang, management).open(player)
    }

    private fun navigate(current: Int, back: Boolean) {
        pageIndex = if (back) current - 1 else current + 1
        open(player)
    }

    private fun mayManage(shop: Shop) =
        management.canEdit(shop, player.uniqueId) || management.canDelete(shop, player.uniqueId)
}

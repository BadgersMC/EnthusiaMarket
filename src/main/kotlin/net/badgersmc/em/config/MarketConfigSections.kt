package net.badgersmc.em.config

import net.badgersmc.nexus.config.Comment
import net.badgersmc.em.config.EnthusiaMarketConfig.Auction
import net.badgersmc.em.config.EnthusiaMarketConfig.Bedrock
import net.badgersmc.em.config.EnthusiaMarketConfig.Database
import net.badgersmc.em.config.EnthusiaMarketConfig.Debug
import net.badgersmc.em.config.EnthusiaMarketConfig.FinderTrail
import net.badgersmc.em.config.EnthusiaMarketConfig.GuildPolicy
import net.badgersmc.em.config.EnthusiaMarketConfig.IpLimiter
import net.badgersmc.em.config.EnthusiaMarketConfig.Lang
import net.badgersmc.em.config.EnthusiaMarketConfig.LumaGuilds
import net.badgersmc.em.config.EnthusiaMarketConfig.Market
import net.badgersmc.em.config.EnthusiaMarketConfig.Particles
import net.badgersmc.em.config.EnthusiaMarketConfig.Pricing
import net.badgersmc.em.config.EnthusiaMarketConfig.Rent
import net.badgersmc.em.config.EnthusiaMarketConfig.RentWarnings
import net.badgersmc.em.config.EnthusiaMarketConfig.Schematics
import net.badgersmc.em.config.EnthusiaMarketConfig.Shop
import net.badgersmc.em.config.EnthusiaMarketConfig.ShopAudit
import net.badgersmc.em.config.EnthusiaMarketConfig.Signs
import net.badgersmc.em.config.EnthusiaMarketConfig.Store

/** Named sections bound by the Nexus configuration loader. */
abstract class MarketConfigSections {
    @Comment("Binding world for stall regions")
    var market: Market = Market()
    @Comment("Periodic rent charge configuration")
    var rent: Rent = Rent()
    @Comment("Informational login warnings; rent remains manually prepaid.")
    var rentWarnings: RentWarnings = RentWarnings()
    @Comment("Auction settings")
    var auction: Auction = Auction()
    @Comment("Sign shop trade settings")
    var shop: Shop = Shop()
    @Comment("LumaGuilds integration")
    var lumaguilds: LumaGuilds = LumaGuilds()
    @Comment("Database settings")
    var database: Database = Database()
    @Comment("Bedrock/Floodgate settings")
    var bedrock: Bedrock = Bedrock()
    @Comment("Debug logging")
    var debug: Debug = Debug()
    @Comment("Localisation / language file selection")
    var lang: Lang = Lang()
    @Comment("Purchase-sign trigger token + permissions (REQ-250..253)")
    var signs: Signs = Signs()
    @Comment("Stall schematic snapshot/restore on claim/unclaim (REQ-270..274)")
    var schematics: Schematics = Schematics()
    @Comment("Stall boundary particle outline rendering (REQ-220 region kinds)")
    var particles: Particles = Particles()
    @Comment("Private direction trails from shop search results.")
    var finderTrail: FinderTrail = FinderTrail()
    @Comment("Guild tariff/embargo player notifications")
    var guildPolicy: GuildPolicy = GuildPolicy()
    @Comment("Periodic shop audit/repair sweeper (IS2-7/8, REQ-294)")
    var shopAudit: ShopAudit = ShopAudit()
    @Comment("Store URL for /store command (IS2-15, REQ-301)")
    var store: Store = Store()
    @Comment("IP-based fairness limits: one active auction bid and/or one owned stall per IP")
    var ipLimiter: IpLimiter = IpLimiter()
    @Comment("Volume-based stall pricing: rent scales with region block volume (width × height × length)")
    var pricing: Pricing = Pricing()
}

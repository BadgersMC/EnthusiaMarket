package net.badgersmc.em.infrastructure.moderation

/** Pre-migration MariaDB layout used to rehearse upgrades from version 27. */
internal val legacyMarketTables = listOf(
    """
        CREATE TABLE stalls (
            id VARCHAR(128) PRIMARY KEY,
            region_id VARCHAR(128) NOT NULL,
            world VARCHAR(128) NOT NULL,
            state VARCHAR(32) NOT NULL,
            owner_type VARCHAR(16) NOT NULL,
            owner_id VARCHAR(128) NOT NULL,
            owner_since BIGINT,
            winning_bid BIGINT NOT NULL DEFAULT 0,
            rent_mode VARCHAR(16) NOT NULL,
            rent_pct DOUBLE NOT NULL DEFAULT 0,
            rent_flat BIGINT NOT NULL DEFAULT 0,
            members TEXT NOT NULL,
            max_members INTEGER NOT NULL DEFAULT -1,
            next_rent_at BIGINT,
            kind VARCHAR(64) NOT NULL DEFAULT 'default',
            extra_entities TEXT NOT NULL,
            extra_total INTEGER NOT NULL DEFAULT 0,
            UNIQUE(world, region_id)
         )
    """.trimIndent(),
    """
        CREATE TABLE shop_items (
            id BIGINT PRIMARY KEY AUTO_INCREMENT,
            stall_id VARCHAR(128) NOT NULL,
            owner VARCHAR(36) NOT NULL,
            sign_world VARCHAR(128) NOT NULL,
            sign_x INTEGER NOT NULL,
            sign_y INTEGER NOT NULL,
            sign_z INTEGER NOT NULL,
            container_world VARCHAR(128) NOT NULL,
            container_x INTEGER NOT NULL,
            container_y INTEGER NOT NULL,
            container_z INTEGER NOT NULL,
            sell_item TEXT NOT NULL,
            sell_amount INTEGER NOT NULL DEFAULT 1,
            cost_item TEXT NOT NULL,
            cost_amount INTEGER NOT NULL DEFAULT 1,
            trusted TEXT NOT NULL,
            hopper_allow_in BOOLEAN NOT NULL DEFAULT TRUE,
            hopper_allow_out BOOLEAN NOT NULL DEFAULT TRUE,
            frozen BOOLEAN NOT NULL DEFAULT FALSE,
            admin_shop BOOLEAN NOT NULL DEFAULT FALSE,
            direction VARCHAR(16) NOT NULL DEFAULT 'SELL',
            search_enabled BOOLEAN NOT NULL DEFAULT TRUE,
            sell_material VARCHAR(128),
            stock_count INTEGER NOT NULL DEFAULT 0,
            FOREIGN KEY (stall_id) REFERENCES stalls(id)
         )
    """.trimIndent(),
    """
        CREATE TABLE auctions (
            id VARCHAR(128) PRIMARY KEY,
            stall_id VARCHAR(128) NOT NULL,
            state VARCHAR(32) NOT NULL,
            start_at BIGINT NOT NULL,
            end_at BIGINT NOT NULL,
            starting_bid BIGINT NOT NULL,
            high_bid_amount BIGINT,
            high_bidder VARCHAR(36),
            high_placed_at BIGINT,
            anti_snipe_sec INTEGER NOT NULL,
            anti_snipe_extend_sec INTEGER NOT NULL DEFAULT 30,
            auction_duration_sec INTEGER NOT NULL DEFAULT 0,
            FOREIGN KEY (stall_id) REFERENCES stalls(id)
         )
    """.trimIndent(),
)

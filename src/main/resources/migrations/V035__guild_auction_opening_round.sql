ALTER TABLE auctions ADD COLUMN opening_round_id VARCHAR(128) NULL;

CREATE TABLE guild_auction_round_wins (
    round_id VARCHAR(128) NOT NULL,
    guild_id VARCHAR(36) NOT NULL,
    stall_id VARCHAR(128) NOT NULL,
    PRIMARY KEY (round_id, guild_id)
);

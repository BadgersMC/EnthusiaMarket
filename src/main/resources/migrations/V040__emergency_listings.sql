CREATE TABLE emergency_listings (
    auction_id VARCHAR(128) NOT NULL,
    shop_id BIGINT NOT NULL,
    direction VARCHAR(8) NOT NULL,
    item VARCHAR(128) NOT NULL,
    quantity INTEGER NOT NULL,
    payment VARCHAR(128) NOT NULL,
    amount INTEGER NOT NULL,
    PRIMARY KEY (auction_id, shop_id),
    FOREIGN KEY (auction_id) REFERENCES auctions(id) ON DELETE CASCADE
);

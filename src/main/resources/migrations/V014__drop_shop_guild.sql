DROP INDEX IF EXISTS idx_shop_guild;
ALTER TABLE shop_items DROP COLUMN IF EXISTS guild_id;
ALTER TABLE shop_items DROP COLUMN IF EXISTS creator_id;

-- Remove DEFAULT 100 on nss_units.capacity
ALTER TABLE nss_units ALTER COLUMN capacity DROP DEFAULT;

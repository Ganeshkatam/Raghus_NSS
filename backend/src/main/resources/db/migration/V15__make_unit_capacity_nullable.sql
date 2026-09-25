-- Drop NOT NULL constraint on nss_units.capacity to make capacity strictly optional/configurable
ALTER TABLE nss_units ALTER COLUMN capacity DROP NOT NULL;

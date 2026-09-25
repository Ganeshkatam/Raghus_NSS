-- V19__support_multi_unit_events.sql
-- Support for Multi-Unit and College-Wide NSS Events

-- 1. Add event_scope column to events table with default 'UNIT'
ALTER TABLE events
    ADD COLUMN IF NOT EXISTS event_scope VARCHAR(20) NOT NULL DEFAULT 'UNIT';

-- Ensure constraint on valid event scopes
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_events_scope'
    ) THEN
        ALTER TABLE events
            ADD CONSTRAINT chk_events_scope
            CHECK (event_scope IN ('UNIT', 'MULTI_UNIT', 'COLLEGE_WIDE'));
    END IF;
END $$;

-- 2. Create association table for event participating units
CREATE TABLE IF NOT EXISTS event_units (
    event_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    CONSTRAINT pk_event_units PRIMARY KEY (event_id, unit_id),
    CONSTRAINT fk_event_units_event FOREIGN KEY (event_id) REFERENCES events(event_id) ON DELETE CASCADE,
    CONSTRAINT fk_event_units_unit FOREIGN KEY (unit_id) REFERENCES nss_units(unit_id) ON DELETE RESTRICT
);

-- 3. Backfill existing events: organizing unit is the initial participating unit
INSERT INTO event_units (event_id, unit_id)
SELECT event_id, unit_id
FROM events
ON CONFLICT DO NOTHING;

-- 4. Create performance indexes
CREATE INDEX IF NOT EXISTS idx_event_units_unit_id ON event_units(unit_id);
CREATE INDEX IF NOT EXISTS idx_event_units_event_id ON event_units(event_id);
CREATE INDEX IF NOT EXISTS idx_events_scope ON events(event_scope);

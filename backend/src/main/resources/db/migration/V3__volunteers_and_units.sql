-- V3__volunteers_and_units.sql
-- NSS Units, Volunteers, and Unit Memberships

CREATE TABLE IF NOT EXISTS volunteers (
    volunteer_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(user_id) ON DELETE CASCADE,
    college_id VARCHAR(50) NOT NULL UNIQUE,
    department VARCHAR(100) NOT NULL,
    year_of_study INT NOT NULL CHECK (year_of_study BETWEEN 1 AND 5),
    join_date DATE NOT NULL DEFAULT CURRENT_DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'ALUMNI')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_volunteers_college_id ON volunteers(college_id);
CREATE INDEX idx_volunteers_department ON volunteers(department);
CREATE INDEX idx_volunteers_status ON volunteers(status);

CREATE TABLE IF NOT EXISTS nss_units (
    unit_id BIGSERIAL PRIMARY KEY,
    unit_name VARCHAR(100) NOT NULL,
    unit_number VARCHAR(20) NOT NULL UNIQUE,
    officer_id BIGINT REFERENCES users(user_id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_nss_units_officer ON nss_units(officer_id);

CREATE TABLE IF NOT EXISTS unit_memberships (
    membership_id BIGSERIAL PRIMARY KEY,
    volunteer_id BIGINT NOT NULL REFERENCES volunteers(volunteer_id) ON DELETE CASCADE,
    unit_id BIGINT NOT NULL REFERENCES nss_units(unit_id) ON DELETE CASCADE,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    left_at TIMESTAMP WITH TIME ZONE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_active_unit_membership UNIQUE (volunteer_id, unit_id, is_active)
);

CREATE INDEX idx_unit_memberships_volunteer ON unit_memberships(volunteer_id);
CREATE INDEX idx_unit_memberships_unit ON unit_memberships(unit_id);

-- V18: Update permission descriptions to use "lists" terminology
-- These descriptions were originally "rosters" in V12 and need updating
-- to match the application-wide terminology change.

UPDATE permissions SET description = 'View NSS units, lists, and officer assignments'
WHERE name = 'UNITS_VIEW' AND description = 'View NSS units, rosters, and officer assignments';

UPDATE permissions SET description = 'View attendance lists and check-in history'
WHERE name = 'ATTENDANCE_VIEW' AND description = 'View attendance rosters and check-in history';

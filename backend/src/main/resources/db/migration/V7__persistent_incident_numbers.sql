-- Allocate ticket numbers atomically across restarts and application instances.
CREATE SEQUENCE IF NOT EXISTS incident_number_seq START WITH 1001;
SELECT setval(
    'incident_number_seq',
    GREATEST(1000, COALESCE(
        (SELECT MAX(SUBSTRING(incident_number FROM 5)::BIGINT)
         FROM incidents WHERE incident_number ~ '^INC-[0-9]+$'), 1000)),
    true
);

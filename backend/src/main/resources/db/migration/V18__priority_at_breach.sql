-- The priority a ticket had when it first missed its resolution target (before the automatic
-- bump), so the IT-manager escalation applies only to tickets that were P1/P2 at that moment.
ALTER TABLE incidents ADD COLUMN priority_at_breach VARCHAR(20);
ALTER TABLE incidents ADD CONSTRAINT chk_incident_priority_at_breach
    CHECK (priority_at_breach IN ('P1_CRITICAL', 'P2_HIGH', 'P3_MEDIUM', 'P4_LOW'));

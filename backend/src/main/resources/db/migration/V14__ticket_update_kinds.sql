-- Ticket "Updates": comments plus entries the system writes for questions, answers,
-- pick-up, resolution and status changes, all in one chronological thread.
ALTER TABLE incident_comments ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'COMMENT';
ALTER TABLE incident_comments ADD CONSTRAINT chk_comment_kind
    CHECK (kind IN ('COMMENT', 'QUESTION', 'ANSWER', 'PICKUP', 'RESOLUTION', 'STATUS'));

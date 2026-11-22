-- Quick fixes already written by the language model, so the same question is not asked twice.
CREATE TABLE ai_advice_cache (
    cache_key VARCHAR(64) PRIMARY KEY,
    kind VARCHAR(20) NOT NULL,
    summary TEXT NOT NULL,
    steps TEXT NOT NULL,
    source VARCHAR(20) NOT NULL,
    it_related BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

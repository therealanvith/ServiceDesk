-- Earlier "not an IT problem" verdicts were sometimes wrong; ask again.
DELETE FROM ai_advice_cache WHERE it_related = FALSE;

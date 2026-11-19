package com.servicedesk.domain.intelligence;

import com.servicedesk.domain.incident.Category;
import java.util.List;
import java.util.Optional;

/** Strategy for suggesting an incident category from the text a reporter writes. */
public interface CategorySuggestionStrategy {

  /** A suggested category, how sure we are (0-1), and the evidence for it. */
  record Suggestion(Category category, double confidence, List<String> matchedKeywords) {}

  Optional<Suggestion> suggest(String title, String description);
}

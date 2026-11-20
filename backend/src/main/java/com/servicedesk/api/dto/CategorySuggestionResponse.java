package com.servicedesk.api.dto;

import com.servicedesk.domain.incident.Category;
import java.util.List;

/** Suggested category with a 0-1 confidence and the words that led to it; null if unsure. */
public record CategorySuggestionResponse(
    Category category, double confidence, List<String> matchedKeywords) {}

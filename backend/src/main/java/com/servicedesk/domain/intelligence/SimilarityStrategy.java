package com.servicedesk.domain.intelligence;

/** Strategy interface for textual similarity detection between incidents. */
public interface SimilarityStrategy {

  /** Calculates similarity between two text snippets as a value between 0.0 and 1.0. */
  double calculateSimilarity(String textA, String textB);
}

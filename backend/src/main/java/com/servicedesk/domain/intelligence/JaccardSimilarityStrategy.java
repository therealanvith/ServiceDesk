package com.servicedesk.domain.intelligence;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Token-based Jaccard similarity implementation.
 *
 * <p>Similarity = |A ∩ B| / |A ∪ B|
 */
public class JaccardSimilarityStrategy implements SimilarityStrategy {

  @Override
  public double calculateSimilarity(String textA, String textB) {
    if (textA == null || textB == null) {
      return 0.0;
    }

    Set<String> tokensA = tokenize(textA);
    Set<String> tokensB = tokenize(textB);

    if (tokensA.isEmpty() && tokensB.isEmpty()) {
      return 0.0;
    }

    Set<String> intersection = new HashSet<>(tokensA);
    intersection.retainAll(tokensB);

    Set<String> union = new HashSet<>(tokensA);
    union.addAll(tokensB);

    if (union.isEmpty()) {
      return 0.0;
    }

    return (double) intersection.size() / (double) union.size();
  }

  private Set<String> tokenize(String text) {
    if (text == null || text.isBlank()) {
      return Collections.emptySet();
    }
    return Arrays.stream(text.toLowerCase().split("[^a-zA-Z0-9]+"))
        .filter(token -> !token.isBlank())
        .collect(Collectors.toSet());
  }
}

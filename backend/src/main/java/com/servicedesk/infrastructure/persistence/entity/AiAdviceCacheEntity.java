package com.servicedesk.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A quick fix the language model wrote earlier, found again by a hash of the question. */
@Entity
@Table(name = "ai_advice_cache")
public class AiAdviceCacheEntity {

  @Id
  @Column(name = "cache_key", nullable = false, length = 64)
  private String cacheKey;

  @Column(name = "kind", nullable = false, length = 20)
  private String kind;

  @Column(name = "summary", nullable = false)
  private String summary;

  /** The steps as a JSON array of strings. */
  @Column(name = "steps", nullable = false)
  private String steps;

  @Column(name = "source", nullable = false, length = 20)
  private String source;

  @Column(name = "it_related", nullable = false)
  private boolean itRelated;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected AiAdviceCacheEntity() {}

  public AiAdviceCacheEntity(
      String cacheKey,
      String kind,
      String summary,
      String steps,
      String source,
      boolean itRelated,
      Instant createdAt) {
    this.cacheKey = cacheKey;
    this.kind = kind;
    this.summary = summary;
    this.steps = steps;
    this.source = source;
    this.itRelated = itRelated;
    this.createdAt = createdAt;
  }

  public String getCacheKey() {
    return cacheKey;
  }

  public String getKind() {
    return kind;
  }

  public String getSummary() {
    return summary;
  }

  public String getSteps() {
    return steps;
  }

  public String getSource() {
    return source;
  }

  public boolean isItRelated() {
    return itRelated;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}

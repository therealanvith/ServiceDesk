package com.servicedesk.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.servicedesk.domain.intelligence.KnowledgeBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class KnowledgeBaseTest {

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "My wifi keeps dropping every few minutes|wifi",
        "cannot connect to the VPN from home|vpn",
        "The printer is offline again|printer-offline",
        "I forgot my password|password",
        "my account is locked out|locked",
        "monitor shows black screen|display",
        "laptop won't turn on at all|boot",
        "clicked a link in a phishing email|phishing",
        "cannot hear anyone in the teams call|video-call"
      })
  void matchesCommonProblems(String text, String expectedId) {
    assertThat(KnowledgeBase.best(text))
        .hasValueSatisfying(a -> assertThat(a.id()).isEqualTo(expectedId));
  }

  @Test
  void shortKeywordsDoNotMatchInsideOtherWords() {
    // "lag" is inside "flag", "ink" inside "link": neither is a problem report.
    assertThat(KnowledgeBase.search("please flag this and send the link", 3)).isEmpty();
  }

  @Test
  void everyArticleIsUsable() {
    assertThat(KnowledgeBase.articles()).hasSizeGreaterThanOrEqualTo(25);
    assertThat(KnowledgeBase.articles().stream().map(KnowledgeBase.Article::id).distinct().count())
        .isEqualTo(KnowledgeBase.articles().size());
    KnowledgeBase.articles()
        .forEach(
            a -> {
              assertThat(a.steps()).hasSizeBetween(3, 4);
              assertThat(a.keywords()).isNotEmpty();
              assertThat(a.escalate()).isNotBlank();
            });
  }

  @Test
  void blankTextMatchesNothing() {
    assertThat(KnowledgeBase.search("  ", 3)).isEmpty();
    assertThat(KnowledgeBase.search(null, 3)).isEmpty();
  }
}

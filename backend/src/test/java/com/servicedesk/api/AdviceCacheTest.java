package com.servicedesk.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.servicedesk.domain.intelligence.IncidentAssistant.AssistantAdvice;
import com.servicedesk.infrastructure.ai.GeminiAssistantAdapter;
import com.servicedesk.infrastructure.persistence.repository.SpringDataAiAdviceCacheJpaRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** A quick fix written by the language model is stored, so the same question is asked once. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdviceCacheTest {

  @Autowired MockMvc mvc;
  @Autowired SpringDataAiAdviceCacheJpaRepository cache;
  @MockBean GeminiAssistantAdapter gemini;

  @BeforeEach
  void setUp() {
    when(gemini.isEnabled()).thenReturn(true);
    when(gemini.source()).thenReturn("gemini");
    when(gemini.selfHelp(any(), any()))
        .thenReturn(new AssistantAdvice("Try these.", List.of("Restart it.", "Check the cable.")));
  }

  private void ask(String title) throws Exception {
    mvc.perform(
            post("/api/v1/incidents/self-help")
                .with(user("someone").roles("EMPLOYEE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + title + "\",\"description\":\"It will not start\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.steps[0]").value("Restart it."))
        .andExpect(jsonPath("$.source").value("gemini"));
  }

  @Test
  void theSameQuestionReachesTheModelOnlyOnce() throws Exception {
    ask("Laptop dead");
    ask("  laptop   DEAD "); // same words, different spacing and case
    verify(gemini, times(1)).selfHelp(any(), any());
    assertThat(cache.count()).isEqualTo(1);
  }

  @Test
  void aDifferentQuestionAsksAgain() throws Exception {
    ask("Laptop dead");
    ask("Monitor dead");
    verify(gemini, times(2)).selfHelp(any(), any());
    assertThat(cache.count()).isEqualTo(2);
  }
}

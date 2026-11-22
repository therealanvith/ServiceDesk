package com.servicedesk.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedesk.domain.incident.Category;
import com.servicedesk.domain.incident.Impact;
import com.servicedesk.domain.incident.Urgency;
import com.servicedesk.domain.intelligence.IncidentAssistant.AssistantAdvice;
import com.servicedesk.domain.intelligence.IncidentAssistant.PastFix;
import com.servicedesk.domain.intelligence.IncidentAssistant.TicketDraft;
import com.servicedesk.domain.intelligence.IncidentAssistant.TriageSuggestion;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GeminiAssistantAdapterTest {

  private static final String URL = "https://gemini.test/v1beta/models/gemini-test:generateContent";
  private final ObjectMapper mapper = new ObjectMapper();
  private MockRestServiceServer server;
  private GeminiAssistantAdapter adapter;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl("https://gemini.test/v1beta");
    server = MockRestServiceServer.bindTo(builder).build();
    adapter = new GeminiAssistantAdapter(builder.build(), mapper, "test-key", "gemini-test");
  }

  /** Wraps the model's JSON text the way generateContent returns it. */
  private String answer(String json) throws Exception {
    return mapper.writeValueAsString(
        java.util.Map.of(
            "candidates",
            List.of(
                java.util.Map.of(
                    "content",
                    java.util.Map.of("parts", List.of(java.util.Map.of("text", json)))))));
  }

  @Test
  void triageSendsASchemaBoundRequestAndParsesTheAnswer() throws Exception {
    server
        .expect(requestTo(URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("x-goog-api-key", "test-key"))
        .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
        .andExpect(
            jsonPath("$.generationConfig.responseSchema.properties.category.enum[0]").exists())
        .andExpect(
            jsonPath("$.systemInstruction.parts[0].text")
                .value(GeminiAssistantAdapter.SYSTEM_INSTRUCTION))
        .andExpect(
            jsonPath("$.contents[0].parts[0].text")
                .value(org.hamcrest.Matchers.containsString("<ticket>")))
        .andRespond(
            withSuccess(
                answer(
                    "{\"category\":\"NETWORK\",\"impact\":\"HIGH\",\"urgency\":\"HIGH\","
                        + "\"serviceName\":\"Campus Wi-Fi\",\"reason\":\"The whole floor is offline.\"}"),
                MediaType.APPLICATION_JSON));

    TriageSuggestion result =
        adapter.triage(
            new TicketDraft("Wi-Fi down", "Nobody on floor 3 can connect"),
            List.of("Campus Wi-Fi", "Email"));

    assertThat(result.category()).isEqualTo(Category.NETWORK);
    assertThat(result.impact()).isEqualTo(Impact.HIGH);
    assertThat(result.urgency()).isEqualTo(Urgency.HIGH);
    assertThat(result.serviceName()).isEqualTo("Campus Wi-Fi");
    server.verify();
  }

  @Test
  void aServiceWeDidNotOfferIsDropped() throws Exception {
    server
        .expect(requestTo(URL))
        .andRespond(
            withSuccess(
                answer(
                    "{\"category\":\"SOFTWARE\",\"impact\":\"LOW\",\"urgency\":\"LOW\","
                        + "\"serviceName\":\"Made-up service\",\"reason\":\"One app.\"}"),
                MediaType.APPLICATION_JSON));

    assertThat(adapter.triage(new TicketDraft("App", "Crashes"), List.of("Email")).serviceName())
        .isNull();
  }

  @Test
  void unexpectedValuesAreRejectedSoTheCallerFallsBack() throws Exception {
    server
        .expect(requestTo(URL))
        .andRespond(
            withSuccess(
                answer(
                    "{\"category\":\"PLUMBING\",\"impact\":\"LOW\",\"urgency\":\"LOW\",\"reason\":\"x\"}"),
                MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> adapter.triage(new TicketDraft("a", "b"), List.of()))
        .isInstanceOf(AssistantUnavailableException.class);
  }

  @Test
  void malformedJsonAndServerErrorsBecomeUnavailable() throws Exception {
    server
        .expect(requestTo(URL))
        .andRespond(withSuccess(answer("not json {"), MediaType.APPLICATION_JSON));
    assertThatThrownBy(() -> adapter.summarize(context()))
        .isInstanceOf(AssistantUnavailableException.class);

    server.reset();
    server.expect(requestTo(URL)).andRespond(withServerError());
    assertThatThrownBy(() -> adapter.summarize(context()))
        .isInstanceOf(AssistantUnavailableException.class);
  }

  @Test
  void adviceIsTrimmedToFourSteps() throws Exception {
    server
        .expect(requestTo(URL))
        .andRespond(
            withSuccess(
                answer(
                    "{\"summary\":\"Try these.\",\"steps\":[\"one\",\"two\",\"\",\"three\",\"four\",\"five\"]}"),
                MediaType.APPLICATION_JSON));

    AssistantAdvice advice =
        adapter.selfHelp(
            new TicketDraft("Printer", "Jammed"), List.of(new PastFix(null, "Cleared tray")));

    assertThat(advice.steps()).containsExactly("one", "two", "three", "four");
  }

  @Test
  void withoutAKeyItIsDisabledAndNeverCallsOut() {
    GeminiAssistantAdapter off =
        new GeminiAssistantAdapter(RestClient.create(), mapper, " ", "gemini-test");
    assertThat(off.isEnabled()).isFalse();
    assertThatThrownBy(() -> off.summarize(context()))
        .isInstanceOf(AssistantUnavailableException.class);
  }

  private static com.servicedesk.domain.intelligence.IncidentAssistant.TicketContext context() {
    return new com.servicedesk.domain.intelligence.IncidentAssistant.TicketContext(
        "INC-1", "Title", "Description", "SOFTWARE", "IN_PROGRESS", "P3_MEDIUM", List.of());
  }
}

package com.servicedesk.api.dto;

import com.servicedesk.domain.incident.Category;
import com.servicedesk.domain.incident.Impact;
import com.servicedesk.domain.incident.Urgency;
import com.servicedesk.domain.priority.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Requests and answers for the help desk assistant. {@code source} is "gemini" or "rules". */
public final class AssistantDtos {

  private AssistantDtos() {}

  public record TriageRequest(
      @NotBlank(message = "Describe the problem first")
          @Size(max = 200, message = "Title must not exceed 200 characters")
          String title,
      @Size(max = 10000, message = "Description must not exceed 10000 characters")
          String description) {}

  public record TriageResponse(
      Category category,
      Impact impact,
      Urgency urgency,
      UUID serviceId,
      String serviceName,
      Priority predictedPriority,
      String reason,
      String source) {}

  public record AdviceResponse(String summary, List<String> steps, String source) {}

  public record SummaryResponse(String summary, String source) {}

  public record SelfHelpFeedbackRequest(
      @NotNull(message = "Say whether it helped") Boolean helped) {}
}

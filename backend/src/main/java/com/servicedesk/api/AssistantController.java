package com.servicedesk.api;

import com.servicedesk.api.dto.AssistantDtos.ChatRequest;
import com.servicedesk.api.dto.AssistantDtos.ChatResponse;
import com.servicedesk.application.AssistantApplicationService;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The help chat available to every signed-in user. */
@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController {

  private final AssistantApplicationService assistant;

  public AssistantController(AssistantApplicationService assistant) {
    this.assistant = assistant;
  }

  @PostMapping("/chat")
  public ResponseEntity<ChatResponse> chat(
      @Valid @RequestBody ChatRequest request, Principal principal) {
    return ResponseEntity.ok(assistant.chat(request, principal.getName()));
  }
}

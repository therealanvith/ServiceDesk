package com.servicedesk.api;

import com.servicedesk.api.dto.CategorySettingResponse;
import com.servicedesk.api.dto.UpdateCategorySettingRequest;
import com.servicedesk.application.CategoryApplicationService;
import com.servicedesk.domain.incident.Category;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Incident categories: everyone reads them, only administrators switch them on or off. */
@RestController
@RequestMapping("/api/v1/catalog/categories")
public class CategoryController {

  private final CategoryApplicationService categories;

  public CategoryController(CategoryApplicationService categories) {
    this.categories = categories;
  }

  @GetMapping
  public ResponseEntity<List<CategorySettingResponse>> list() {
    return ResponseEntity.ok(categories.list());
  }

  @PutMapping("/{category}")
  @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'ROOT')")
  public ResponseEntity<CategorySettingResponse> update(
      @PathVariable Category category, @Valid @RequestBody UpdateCategorySettingRequest request) {
    return ResponseEntity.ok(categories.update(category, request.active()));
  }
}

package com.servicedesk.application;

import com.servicedesk.api.dto.CategorySettingResponse;
import com.servicedesk.domain.incident.Category;
import com.servicedesk.infrastructure.persistence.entity.CategorySettingEntity;
import com.servicedesk.infrastructure.persistence.repository.SpringDataCategorySettingJpaRepository;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Administrator-managed list of incident categories offered to reporters. */
@Service
@Transactional
public class CategoryApplicationService {

  private final SpringDataCategorySettingJpaRepository settings;

  public CategoryApplicationService(SpringDataCategorySettingJpaRepository settings) {
    this.settings = settings;
  }

  /** Every category with its switch; a category with no row yet counts as active. */
  @Transactional(readOnly = true)
  public List<CategorySettingResponse> list() {
    return Arrays.stream(Category.values())
        .map(
            c ->
                new CategorySettingResponse(
                    c,
                    settings.findById(c.name()).map(CategorySettingEntity::isActive).orElse(true)))
        .toList();
  }

  public CategorySettingResponse update(Category category, boolean active) {
    if (!active
        && list().stream().filter(CategorySettingResponse::active).count() <= 1
        && settings.findById(category.name()).map(CategorySettingEntity::isActive).orElse(true)) {
      throw new IllegalArgumentException("At least one category must stay available");
    }
    CategorySettingEntity row =
        settings
            .findById(category.name())
            .orElseGet(() -> new CategorySettingEntity(category.name(), true));
    row.setActive(active);
    settings.save(row);
    return new CategorySettingResponse(category, active);
  }
}

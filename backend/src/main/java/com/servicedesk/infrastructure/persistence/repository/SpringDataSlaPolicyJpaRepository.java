package com.servicedesk.infrastructure.persistence.repository;

import com.servicedesk.domain.priority.Priority;
import com.servicedesk.infrastructure.persistence.entity.SlaPolicyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataSlaPolicyJpaRepository extends JpaRepository<SlaPolicyEntity, Priority> {}

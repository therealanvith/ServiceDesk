package com.servicedesk.infrastructure.persistence.repository;

import com.servicedesk.infrastructure.persistence.entity.AiAdviceCacheEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataAiAdviceCacheJpaRepository
    extends JpaRepository<AiAdviceCacheEntity, String> {}

package com.fieldforcepro.repository;

import com.fieldforcepro.model.Activity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityRepository extends JpaRepository<Activity, String> {

    Page<Activity> findAllByOrderByOccurredAtDesc(Pageable pageable);

    Page<Activity> findByAgentIdOrderByOccurredAtDesc(String agentId, Pageable pageable);
}

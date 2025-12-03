package com.fieldforcepro.repository;

import com.fieldforcepro.model.Lead;
import com.fieldforcepro.model.LeadPriority;
import com.fieldforcepro.model.LeadStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeadRepository extends JpaRepository<Lead, String> {

    @Query("SELECT l FROM Lead l WHERE (:status IS NULL OR l.status = :status) " +
            "AND (:assignedAgentId IS NULL OR l.assignedAgentId = :assignedAgentId) " +
            "AND (:source IS NULL OR l.source = :source) " +
            "AND (:priority IS NULL OR l.priority = :priority) " +
            "AND (:search IS NULL OR LOWER(l.companyName) LIKE LOWER(CONCAT('%', :search, '%')) " +
            " OR LOWER(l.email) LIKE LOWER(CONCAT('%', :search, '%')) " +
            " OR LOWER(l.phone) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Lead> searchLeads(@Param("status") LeadStatus status,
                           @Param("assignedAgentId") String assignedAgentId,
                           @Param("source") String source,
                           @Param("priority") LeadPriority priority,
                           @Param("search") String search,
                           Pageable pageable);
}

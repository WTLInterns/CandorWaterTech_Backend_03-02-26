package com.fieldforcepro.repository;

import com.fieldforcepro.model.LeadComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeadCommentRepository extends JpaRepository<LeadComment, String> {

    List<LeadComment> findByLeadIdOrderByCreatedAtAsc(String leadId);
}

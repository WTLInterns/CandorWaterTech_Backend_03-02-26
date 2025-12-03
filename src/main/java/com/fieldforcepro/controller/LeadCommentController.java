package com.fieldforcepro.controller;

import com.fieldforcepro.model.LeadComment;
import com.fieldforcepro.repository.LeadCommentRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/leads/{leadId}/comments")
@Tag(name = "Lead Comments")
public class LeadCommentController {

    private final LeadCommentRepository leadCommentRepository;

    public LeadCommentController(LeadCommentRepository leadCommentRepository) {
        this.leadCommentRepository = leadCommentRepository;
    }

    @GetMapping
    @Operation(summary = "List comments for a lead")
    public List<LeadComment> list(@PathVariable("leadId") String leadId) {
        return leadCommentRepository.findByLeadIdOrderByCreatedAtAsc(leadId);
    }

    public record CreateCommentRequest(String message, String source, String agentName) { }

    @PostMapping
    @Operation(summary = "Add a comment to a lead")
    public ResponseEntity<LeadComment> create(@PathVariable("leadId") String leadId,
                                              @RequestBody CreateCommentRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        String src = request.source();
        // If explicit source is provided, trust it; otherwise, infer from presence of agentName
        if (src == null || src.isBlank()) {
            if (request.agentName() != null && !request.agentName().isBlank()) {
                src = "AGENT";
            } else {
                src = "ADMIN";
            }
        }
        LeadComment comment = LeadComment.builder()
                .leadId(leadId)
                .message(request.message().trim())
                .source(src)
                .agentName(request.agentName())
                .build();
        LeadComment saved = leadCommentRepository.save(comment);
        return ResponseEntity.ok(saved);
    }
}

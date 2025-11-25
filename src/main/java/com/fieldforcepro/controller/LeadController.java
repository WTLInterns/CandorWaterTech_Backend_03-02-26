package com.fieldforcepro.controller;

import com.fieldforcepro.model.Lead;
import com.fieldforcepro.model.LeadPriority;
import com.fieldforcepro.model.LeadStatus;
import com.fieldforcepro.repository.LeadRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Optional;

@RestController
@RequestMapping("/leads")
@Tag(name = "Leads")
public class LeadController {

    private final LeadRepository leadRepository;

    public LeadController(LeadRepository leadRepository) {
        this.leadRepository = leadRepository;
    }

    @PostMapping
    @Operation(summary = "Create lead")
    public ResponseEntity<Lead> create(@RequestBody Lead lead) {
        Lead saved = leadRepository.save(lead);
        return ResponseEntity.created(URI.create("/leads/" + saved.getId())).body(saved);
    }

    @GetMapping
    @Operation(summary = "List leads with filters")
    public Page<Lead> list(@RequestParam(name = "status", required = false) LeadStatus status,
                           @RequestParam(name = "assignedAgentId", required = false) String assignedAgentId,
                           @RequestParam(name = "source", required = false) String source,
                           @RequestParam(name = "priority", required = false) LeadPriority priority,
                           @RequestParam(name = "search", required = false) String search,
                           @RequestParam(name = "page", defaultValue = "0") int page,
                           @RequestParam(name = "size", defaultValue = "20") int size,
                           @RequestParam(name = "sort", defaultValue = "createdAt,desc") String sort) {
        String[] sortParts = sort.split(",");
        Sort s = Sort.by(Sort.Direction.fromString(sortParts[1]), sortParts[0]);
        Pageable pageable = PageRequest.of(page, size, s);
        return leadRepository.searchLeads(status, assignedAgentId, source, priority, search, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get lead by id")
    public ResponseEntity<Lead> get(@PathVariable String id) {
        Optional<Lead> lead = leadRepository.findById(id);
        return lead.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update lead (full)")
    public ResponseEntity<Lead> update(@PathVariable String id, @RequestBody Lead update) {
        return leadRepository.findById(id)
                .map(existing -> {
                    update.setId(existing.getId());
                    Lead saved = leadRepository.save(update);
                    return ResponseEntity.ok(saved);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete lead")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        if (!leadRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        leadRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}

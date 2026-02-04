package com.fieldforcepro.controller;

import com.fieldforcepro.model.Activity;
import com.fieldforcepro.model.ActivityStatus;
import com.fieldforcepro.repository.ActivityRepository;
import com.fieldforcepro.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@RestController
@RequestMapping("/activities")
@Tag(name = "Activities")
public class ActivityController {

    private final ActivityRepository activityRepository;
    private final UserRepository userRepository;

    public ActivityController(ActivityRepository activityRepository, UserRepository userRepository) {
        this.activityRepository = activityRepository;
        this.userRepository = userRepository;
    }

    public record CreateActivityRequest(
            Long agentId,
            String customerName,
            String activity,
            String status
    ) {}

    public record ActivityResponse(
            String id,
            String time,
            String agent,
            String customer,
            String activity,
            String status,
            Instant occurredAt
    ) {}

    @PostMapping
    @Operation(summary = "Create a new activity from an agent (mobile app)")
    public ResponseEntity<ActivityResponse> create(@RequestBody CreateActivityRequest req) {
        if (req.agentId() == null || req.activity() == null || req.activity().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String agentName = userRepository.findById(req.agentId())
                .map(u -> u.getName() != null ? u.getName() : (u.getEmail() != null ? u.getEmail() : "Agent"))
                .orElse("Agent");

        ActivityStatus status;
        try {
            status = req.status() == null ? ActivityStatus.IN_PROGRESS : ActivityStatus.valueOf(req.status().toUpperCase(Locale.ROOT).replace(' ', '_'));
        } catch (IllegalArgumentException ex) {
            status = ActivityStatus.IN_PROGRESS;
        }

        Activity entity = Activity.builder()
                .agentId(req.agentId())
                .agentName(agentName)
                .customerName(req.customerName())
                .activity(req.activity())
                .status(status)
                .occurredAt(Instant.now())
                .build();

        Activity saved = activityRepository.save(entity);
        return new ResponseEntity<>(toResponse(saved), HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "List activities (optionally filter by agent)")
    public Page<ActivityResponse> list(
            @RequestParam(name = "agentId", required = false) String agentId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size
    ) {
        if (page < 0) page = 0;
        if (size <= 0) size = 50;
        if (size > 200) size = 200;
        Pageable pageable = PageRequest.of(page, size);
        Page<Activity> srcPage;
        if (agentId != null && !agentId.isBlank()) {
            srcPage = activityRepository.findByAgentIdOrderByOccurredAtDesc(agentId, pageable);
        } else {
            srcPage = activityRepository.findAllByOrderByOccurredAtDesc(pageable);
        }
        return srcPage.map(this::toResponse);
    }

    @GetMapping("/latest")
    @Operation(summary = "Get latest activities for dashboard view")
    public List<ActivityResponse> latest(@RequestParam(name = "limit", defaultValue = "10") int limit) {
        if (limit <= 0) {
            limit = 10;
        }
        if (limit > 100) {
            limit = 100;
        }
        return activityRepository.findAllByOrderByOccurredAtDesc(PageRequest.of(0, limit))
                .map(this::toResponse)
                .getContent();
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing activity")
    public ResponseEntity<ActivityResponse> update(
            @PathVariable("id") String id,
            @RequestBody CreateActivityRequest req
    ) {
        Optional<Activity> existingOpt = activityRepository.findById(id);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        Activity existing = existingOpt.get();

        if (req.activity() != null && !req.activity().isBlank()) {
            existing.setActivity(req.activity());
        }
        if (req.customerName() != null) {
            existing.setCustomerName(req.customerName());
        }
        if (req.status() != null) {
            try {
                ActivityStatus newStatus = ActivityStatus.valueOf(req.status().toUpperCase(Locale.ROOT).replace(' ', '_'));
                existing.setStatus(newStatus);
            } catch (IllegalArgumentException ignored) {
                // keep old status
            }
        }

        Activity saved = activityRepository.save(existing);
        return ResponseEntity.ok(toResponse(saved));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an activity")
    public ResponseEntity<Void> delete(@PathVariable("id") String id) {
        if (!activityRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        activityRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private ActivityResponse toResponse(Activity a) {
        ZoneId zoneId = ZoneId.systemDefault();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("hh:mm a")
                .withLocale(Locale.ENGLISH)
                .withZone(zoneId);
        String timeStr = fmt.format(Optional.ofNullable(a.getOccurredAt()).orElse(Instant.now()));
        return new ActivityResponse(
                a.getId(),
                timeStr,
                a.getAgentName(),
                a.getCustomerName(),
                a.getActivity(),
                a.getStatus() != null ? a.getStatus().name() : ActivityStatus.IN_PROGRESS.name(),
                a.getOccurredAt()
        );
    }
}

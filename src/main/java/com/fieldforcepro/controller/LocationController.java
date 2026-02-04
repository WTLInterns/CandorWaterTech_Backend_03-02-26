package com.fieldforcepro.controller;

import com.fieldforcepro.model.Location;
import com.fieldforcepro.repository.LocationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/location")
@Tag(name = "Location")
public class LocationController {

    private final LocationRepository locationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public LocationController(LocationRepository locationRepository, SimpMessagingTemplate messagingTemplate) {
        this.locationRepository = locationRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @PostMapping("/update")
    @Operation(summary = "Update agent location and broadcast to map subscribers")
    public void update(@RequestBody LocationUpdateRequest request) {
        Location location = Location.builder()
                .agentId(request.agentId())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .accuracy(request.accuracy())
                .status(request.status())
                .timestamp(request.timestamp() != null ? request.timestamp() : Instant.now())
                .build();

        Location saved = locationRepository.save(location);
        messagingTemplate.convertAndSend("/topic/locations", saved);
    }

    @GetMapping("/online")
    @Operation(summary = "Get latest location per agent")
    public List<Location> online() {
        return locationRepository.findLatestPerAgent();
    }

    public record LocationUpdateRequest(
            Long agentId,
            Double latitude,
            Double longitude,
            Float accuracy,
            String status,
            Instant timestamp
    ) {
    }
}

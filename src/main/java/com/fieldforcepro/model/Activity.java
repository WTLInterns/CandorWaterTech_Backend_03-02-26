package com.fieldforcepro.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "activities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Activity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(length = 36, nullable = false)
    private String agentId;

    @Column(length = 128)
    private String agentName;

    @Column(length = 256)
    private String customerName;

    @Lob
    private String activity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ActivityStatus status;

    @Column(nullable = false)
    private Instant occurredAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
        if (status == null) {
            status = ActivityStatus.IN_PROGRESS;
        }
    }
}

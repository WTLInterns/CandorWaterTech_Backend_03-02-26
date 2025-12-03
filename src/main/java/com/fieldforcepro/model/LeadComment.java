package com.fieldforcepro.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lead_comments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeadComment {

    @Id
    @Column(length = 36)
    private String id;

    @Column(length = 36, nullable = false)
    private String leadId;

    @Lob
    @Column(nullable = false)
    private String message;

    // Source of the comment, e.g. 'ADMIN' or 'AGENT'
    @Column(length = 20)
    private String source;

    // Optional agent display name for AGENT comments
    @Column(length = 255)
    private String agentName;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}

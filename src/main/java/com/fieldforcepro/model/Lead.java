package com.fieldforcepro.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "leads")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Lead {

    @Id
    @Column(length = 36)
    private String id;

    private String companyName;
    private String phone;
    private String email;
    private String address;

    // Product and quantity for the lead
    private String product;

    private Integer quantity;

    // Total amount for the lead (quantity * unitPrice)
    private Double amount;

    @Column
    private Double latitude;

    @Column
    private Double longitude;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private LeadStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private LeadPriority priority;

    @Column(length = 36)
    private String assignedAgentId;

    private String source;

    @Lob
    private String notes;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}

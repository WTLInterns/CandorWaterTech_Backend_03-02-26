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
@Table(name = "attendance_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceRecord {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 36)
    private String agentId;

    @Column(nullable = false, length = 255)
    private String agentName;

    @Column(nullable = false)
    private Instant checkInTime;

    private Instant checkOutTime;

    @Column(nullable = false, length = 50)
    private String status;

    // Work context: e.g. OFFICE, HOME, FIELD
    @Column(length = 20)
    private String workType;

    // Optional reason: e.g. LATE, HALF_DAY, etc.
    @Column(length = 100)
    private String reason;

    // Optional location for field work
    private Double latitude;

    private Double longitude;

    // Optional URL or path to an attendance image captured in the field
    @Column(length = 500)
    private String imageUrl;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (checkInTime == null) {
            checkInTime = Instant.now();
        }
    }
}

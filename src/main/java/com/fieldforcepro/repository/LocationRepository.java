package com.fieldforcepro.repository;

import com.fieldforcepro.model.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface LocationRepository extends JpaRepository<Location, Long> {

    List<Location> findByAgentId(Long agentId);

    @Query("SELECT l FROM Location l WHERE l.timestamp = (SELECT MAX(l2.timestamp) FROM Location l2 WHERE l2.agentId = l.agentId)")
    List<Location> findLatestPerAgent();
}

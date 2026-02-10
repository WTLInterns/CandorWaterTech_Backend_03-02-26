package com.fieldforcepro.repository;

import com.fieldforcepro.model.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface LocationRepository extends JpaRepository<Location, Long> {

    List<Location> findByAgentId(Long agentId);

    @Query(
            value = "SELECT l.* " +
                    "FROM locations l " +
                    "JOIN (" +
                    "  SELECT agent_id, MAX(timestamp) AS max_ts " +
                    "  FROM locations " +
                    "  GROUP BY agent_id" +
                    ") t " +
                    "ON l.agent_id = t.agent_id AND l.timestamp = t.max_ts",
            nativeQuery = true
    )
    List<Location> findLatestPerAgent();
}

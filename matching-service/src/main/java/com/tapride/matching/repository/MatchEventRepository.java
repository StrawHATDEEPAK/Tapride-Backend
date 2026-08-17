package com.tapride.matching.repository;

import com.tapride.matching.domain.MatchEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MatchEventRepository extends JpaRepository<MatchEvent, UUID> {
    List<MatchEvent> findByRideIdOrderByOccurredAtAsc(UUID rideId);
}

package com.tapride.matching.repository;

import com.tapride.matching.domain.DriverMatch;
import com.tapride.matching.domain.MatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DriverMatchRepository extends JpaRepository<DriverMatch, UUID> {
    Optional<DriverMatch> findByRideId(UUID rideId);

    /** Used by DriverLocationSimulator to find every match currently "in flight" toward pickup. */
    List<DriverMatch> findByStatusIn(List<MatchStatus> statuses);
}

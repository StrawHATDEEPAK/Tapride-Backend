package com.tapride.order.repository;

import com.tapride.order.domain.RideEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RideEventRepository extends JpaRepository<RideEvent, UUID> {
    List<RideEvent> findByRideIdOrderByOccurredAtAsc(UUID rideId);
}

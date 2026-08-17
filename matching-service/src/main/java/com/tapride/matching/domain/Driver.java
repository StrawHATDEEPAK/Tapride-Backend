package com.tapride.matching.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Static driver profile - name and vehicle info that basically never changes.
 * Deliberately does NOT store current location or availability here; those are
 * live, frequently-updated, and queried geospatially, which is exactly what
 * Redis (GEOADD/GEOSEARCH) is built for and Postgres is not. This split -
 * durable profile data in Postgres, hot/live state in Redis - is a common
 * real-world pattern, not just a demo shortcut.
 */
@Entity
@Table(name = "drivers")
@Getter
@Setter
@NoArgsConstructor
public class Driver {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String vehicle;

    public Driver(UUID id, String name, String vehicle) {
        this.id = id;
        this.name = name;
        this.vehicle = vehicle;
    }
}

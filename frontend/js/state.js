/**
 * Single client-side store. Every normalized WebSocket event flows through
 * applyEvent(); every renderer (feed list, status ladder, map) subscribes to
 * this instead of talking to the WebSocket directly. This is the "React
 * Context" shaped piece if this ever becomes a real React app later - the
 * store's public shape wouldn't need to change, only what consumes it.
 *
 * Deliberately simple: no persistence, no undo, just an in-memory Map plus
 * pub/sub via EventTarget (a real browser API, not a hand-rolled emitter).
 */

// Ladder progression is driven ONLY by order-service's own event log - it's
// the authoritative saga state machine. payment-service/matching-service also
// publish their own copies of similar events, which would be redundant (and
// arrive via a slightly different path) for driving the ladder - they're only
// used here for DRIVER_LOCATION_UPDATED, which nothing else provides.
const FAILURE_EVENTS = new Set([
    'RIDE_VALIDATION_FAILED', 'PAYMENT_FAILED', 'DRIVER_MATCH_FAILED', 'RIDE_CANCELLED',
]);

class RideStore extends EventTarget {
    constructor() {
        super();
        /** @type {Map<string, object>} */
        this.rides = new Map();
        this.selectedRideId = null;
    }

    applyEvent(event) {
        const { service, rideId, eventType, payload } = event;

        // Driver position updates aren't part of the ladder - handled separately
        // and dispatched as a lighter 'position' event so the map can update
        // without forcing the whole feed/ladder to re-render every ~4 seconds.
        if (eventType === 'DRIVER_LOCATION_UPDATED') {
            const ride = this.rides.get(rideId);
            if (ride) {
                ride.driverPos = { lat: payload.lat, lng: payload.lng };
                ride.driverId = payload.driverId ?? ride.driverId;
                this.dispatchEvent(new CustomEvent('position', { detail: { rideId } }));
            }
            return;
        }

        // Only order-service's own log drives the ladder (see comment above).
        if (service !== 'order-service') return;

        let ride = this.rides.get(rideId);
        if (!ride) {
            ride = {
                id: rideId,
                pickup: { lat: payload.pickupLat, lng: payload.pickupLng },
                dropoff: { lat: payload.dropoffLat, lng: payload.dropoffLng },
                fare: null,
                driverId: null,
                driverPos: null,
                status: eventType,
                failed: false,
                failReason: null,
                seen: new Set(),
                history: [],
                createdAt: event.occurredAt,
            };
            this.rides.set(rideId, ride);
        }

        ride.seen.add(eventType);
        ride.status = eventType;
        ride.history.push({ eventType, occurredAt: event.occurredAt, payload });

        if (payload.estimatedFare != null) ride.fare = payload.estimatedFare;
        if (payload.driverId != null) ride.driverId = payload.driverId;
        if (FAILURE_EVENTS.has(eventType)) {
            ride.failed = true;
            ride.failReason = payload.reason ?? eventType;
        }

        if (this.selectedRideId === null) this.selectedRideId = rideId; // auto-select the first ride seen

        this.dispatchEvent(new CustomEvent('change', { detail: { rideId } }));
    }

    select(rideId) {
        this.selectedRideId = rideId;
        this.dispatchEvent(new CustomEvent('select', { detail: { rideId } }));
    }

    getSelected() {
        return this.selectedRideId ? this.rides.get(this.selectedRideId) : null;
    }

    /** Rides ordered newest-first, for the feed list. */
    allRidesNewestFirst() {
        return [...this.rides.values()].sort((a, b) => b.createdAt.localeCompare(a.createdAt));
    }
}

export const store = new RideStore();

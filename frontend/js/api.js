/**
 * All outbound REST calls in one place. Nothing else in the frontend builds
 * a URL or calls fetch() directly - keeps the "which service owns which
 * endpoint" knowledge in a single file, matching how each backend service's
 * own client code is organized.
 */
export async function bookRide(config, { pickup, dropoff, riderId }) {
    const res = await fetch(`${config.API.ORDER}/api/rides`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            riderId,
            pickupLat: pickup.lat, pickupLng: pickup.lng,
            dropoffLat: dropoff.lat, dropoffLng: dropoff.lng,
        }),
    });
    if (!res.ok) throw new Error(`Booking failed: ${res.status}`);
    return res.json();
}

export async function setChaos(baseUrl, settings) {
    const res = await fetch(`${baseUrl}/api/chaos`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(settings),
    });
    if (!res.ok) throw new Error(`Chaos toggle failed: ${res.status}`);
    return res.json();
}

export async function getChaos(baseUrl) {
    const res = await fetch(`${baseUrl}/api/chaos`);
    if (!res.ok) throw new Error(`Chaos fetch failed: ${res.status}`);
    return res.json();
}

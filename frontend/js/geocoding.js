/**
 * Wraps OpenStreetMap's free Nominatim geocoding service. No API key, no
 * signup - but it has a real usage policy (max 1 request/second, and it
 * expects a Referer identifying the app, which browsers send automatically
 * and can't be spoofed - that's why Nominatim accepts it as identification).
 * See https://operations.osmfoundation.org/policies/nominatim/
 *
 * Debouncing search-as-you-type is not optional here - it's what keeps this
 * app within the 1 req/s policy while someone is actively typing.
 */
const NOMINATIM_BASE = 'https://nominatim.openstreetmap.org';
const DEBOUNCE_MS = 600; // comfortably under 1 req/s even with fast typing

let debounceTimer = null;

/** Debounced forward search - calls onResults(results) at most ~1.6x/second. */
export function searchPlaces(query, onResults) {
    clearTimeout(debounceTimer);
    if (!query || query.trim().length < 3) {
        onResults([]);
        return;
    }
    debounceTimer = setTimeout(async () => {
        try {
            const url = `${NOMINATIM_BASE}/search?format=jsonv2&q=${encodeURIComponent(query)}&limit=5`;
            const res = await fetch(url);
            const data = await res.json();
            onResults(data.map((r) => ({
                label: r.display_name,
                lat: parseFloat(r.lat),
                lng: parseFloat(r.lon),
            })));
        } catch {
            onResults([]);
        }
    }, DEBOUNCE_MS);
}

/** Reverse geocode a single point - used to label a pin dropped by click/drag. */
export async function reverseGeocode(lat, lng) {
    try {
        const url = `${NOMINATIM_BASE}/reverse?format=jsonv2&lat=${lat}&lon=${lng}`;
        const res = await fetch(url);
        const data = await res.json();
        return data.display_name ?? `${lat.toFixed(4)}, ${lng.toFixed(4)}`;
    } catch {
        return `${lat.toFixed(4)}, ${lng.toFixed(4)}`;
    }
}

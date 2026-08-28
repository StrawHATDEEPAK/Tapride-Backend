import { store } from './state.js';

function makeMarkerEl(color, size, glide) {
    const el = document.createElement('div');
    el.style.width = `${size}px`;
    el.style.height = `${size}px`;
    el.style.borderRadius = '50%';
    el.style.background = color;
    el.style.border = '2px solid rgba(255,255,255,0.9)';
    el.style.boxShadow = `0 0 0 5px ${color}33`;
    if (glide) {
        // Mapbox sets this element's transform imperatively on every
        // setLngLat() call - since that's just a normal CSS property change,
        // this transition rule makes the browser animate it smoothly between
        // simulator ticks (~4s apart) instead of the marker jumping instantly.
        // Set slightly under the tick interval so it settles before the next update.
        el.style.transition = 'transform 3.8s linear';
    }
    return el;
}

export function initMapRenderer(config) {
    const map = new maplibregl.Map({
        container: 'map',
        style: config.MAP_STYLE_URL,
        center: [config.MAP.centerLng, config.MAP.centerLat],
        zoom: config.MAP.zoom,
    });

    let pickupMarker = null;
    let dropoffMarker = null;
    let driverMarker = null;
    let lastRenderedRideId = null;

    function clearMarkers() {
        pickupMarker?.remove();
        dropoffMarker?.remove();
        driverMarker?.remove();
        pickupMarker = dropoffMarker = driverMarker = null;
    }

    /** Full rebuild - only runs when the SELECTED ride changes, not on every position tick. */
    function rebuildForSelection() {
        const ride = store.getSelected();
        clearMarkers();
        lastRenderedRideId = ride?.id ?? null;
        if (!ride) return;

        pickupMarker = new maplibregl.Marker({ element: makeMarkerEl('#3FB8AF', 14, false) })
            .setLngLat([ride.pickup.lng, ride.pickup.lat])
            .setPopup(new maplibregl.Popup({ offset: 12 }).setText('Pickup'))
            .addTo(map);

        dropoffMarker = new maplibregl.Marker({ element: makeMarkerEl('#E8604C', 14, false) })
            .setLngLat([ride.dropoff.lng, ride.dropoff.lat])
            .setPopup(new maplibregl.Popup({ offset: 12 }).setText('Dropoff'))
            .addTo(map);

        if (ride.driverPos) {
            driverMarker = new maplibregl.Marker({ element: makeMarkerEl('#F2A340', 18, true) })
                .setLngLat([ride.driverPos.lng, ride.driverPos.lat])
                .addTo(map);
        }

        const bounds = new maplibregl.LngLatBounds()
            .extend([ride.pickup.lng, ride.pickup.lat])
            .extend([ride.dropoff.lng, ride.dropoff.lat]);
        map.fitBounds(bounds, { padding: 80, maxZoom: 14, duration: 600 });
    }

    /** Lightweight update - just glides the driver marker, no rebuild/refit. */
    function updateDriverPosition() {
        const ride = store.getSelected();
        if (!ride || ride.id !== lastRenderedRideId || !ride.driverPos) return;

        if (!driverMarker) {
            driverMarker = new maplibregl.Marker({ element: makeMarkerEl('#F2A340', 18, true) })
                .setLngLat([ride.driverPos.lng, ride.driverPos.lat])
                .addTo(map);
        } else {
            driverMarker.setLngLat([ride.driverPos.lng, ride.driverPos.lat]);
        }
    }

    store.addEventListener('select', rebuildForSelection);
    store.addEventListener('change', () => {
        // Auto-selection of the first-ever ride fires a 'change', not a
        // 'select' - catch that case too, but only rebuild if the selection
        // actually differs from what's currently drawn.
        if (store.selectedRideId !== lastRenderedRideId) rebuildForSelection();
    });
    store.addEventListener('position', (e) => {
        if (e.detail.rideId === store.selectedRideId) updateDriverPosition();
    });
}

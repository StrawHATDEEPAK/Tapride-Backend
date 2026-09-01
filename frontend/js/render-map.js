import { store } from './state.js';
import { reverseGeocode } from './geocoding.js';

/**
 * Single map controller for the whole app - handles two distinct modes:
 *   1. BOOKING mode: user is placing pickup/dropoff pins (click or search)
 *   2. TRACKING mode: showing the selected ride's live saga (driver moving)
 * Both share the same map instance; booking pins get cleared once a ride
 * is successfully created and becomes the tracked ride.
 */
class MapController extends EventTarget {
    constructor() {
        super();
        this.map = null;
        this.bookingPins = { pickup: null, dropoff: null }; // { marker, lat, lng, label }
        this.pickMode = null; // 'pickup' | 'dropoff' | null

        this.selectedMarkers = { pickup: null, dropoff: null, driver: null };
        this.cityDots = new Map(); // rideId -> marker, for every ride NOT currently selected
        this.trails = new Map();   // rideId -> array of [lng,lat], capped
        this.animTokens = new Map(); // rideId -> incrementing token, cancels stale animation frames
        this.lastRenderedRideId = null;
    }

    async init(config) {
        const center = await this._resolveInitialCenter(config);

        this.map = new maplibregl.Map({
            container: 'map',
            style: config.MAP_STYLE_URL,
            center: [center.lng, center.lat],
            zoom: config.MAP.zoom,
        });

        this.map.on('click', (e) => this._handleMapClick(e));

        this.map.on('load', () => {
            this.map.addSource('driver-trail', {
                type: 'geojson',
                lineMetrics: true, // required for line-gradient to work
                data: { type: 'FeatureCollection', features: [] },
            });
            this.map.addLayer({
                id: 'driver-trail-layer',
                type: 'line',
                source: 'driver-trail',
                paint: {
                    'line-width': 3,
                    // Fades from transparent (oldest point) to solid teal (current position) -
                    // a real visual trail, not just a static line.
                    'line-gradient': [
                        'interpolate', ['linear'], ['line-progress'],
                        0, 'rgba(63,184,175,0)',
                        1, 'rgba(63,184,175,0.8)',
                    ],
                },
            });
        });

        store.addEventListener('change', () => this._onStoreChange());
        store.addEventListener('select', () => this._onSelectionChange());
        store.addEventListener('position', (e) => this._onPosition(e.detail.rideId));
    }

    async _resolveInitialCenter(config) {
        if (!navigator.geolocation) return { lat: config.MAP.centerLat, lng: config.MAP.centerLng };
        return new Promise((resolve) => {
            const timeout = setTimeout(() => resolve({ lat: config.MAP.centerLat, lng: config.MAP.centerLng }), 4000);
            navigator.geolocation.getCurrentPosition(
                (pos) => {
                    clearTimeout(timeout);
                    resolve({ lat: pos.coords.latitude, lng: pos.coords.longitude });
                },
                () => {
                    clearTimeout(timeout);
                    resolve({ lat: config.MAP.centerLat, lng: config.MAP.centerLng });
                },
                { timeout: 3500 }
            );
        });
    }

    // ---------------- Booking mode ----------------

    enterPickMode(which) {
        this.pickMode = which;
        this.map.getCanvas().style.cursor = 'crosshair';
    }

    async _handleMapClick(e) {
        if (!this.pickMode) return;
        const { lat, lng } = e.lngLat;
        await this._setBookingPin(this.pickMode, lat, lng);
        this.pickMode = null;
        this.map.getCanvas().style.cursor = '';
    }

    async _setBookingPin(which, lat, lng) {
        const color = which === 'pickup' ? '#3FB8AF' : '#E8604C';
        let pin = this.bookingPins[which];

        if (!pin) {
            const el = makeMarkerEl(color, 16, false);
            const marker = new maplibregl.Marker({ element: el, draggable: true })
                .setLngLat([lng, lat])
                .addTo(this.map);
            marker.on('dragend', () => {
                const pos = marker.getLngLat();
                this._setBookingPin(which, pos.lat, pos.lng);
            });
            pin = { marker, lat, lng, label: '' };
            this.bookingPins[which] = pin;
        } else {
            pin.marker.setLngLat([lng, lat]);
            pin.lat = lat;
            pin.lng = lng;
        }

        pin.label = 'Locating address…';
        this._dispatchSelectionChange();
        pin.label = await reverseGeocode(lat, lng);
        this._dispatchSelectionChange();
    }

    /** Called by booking-form.js when a search-box result is chosen. */
    setPointFromSearch(which, { lat, lng, label }) {
        const color = which === 'pickup' ? '#3FB8AF' : '#E8604C';
        let pin = this.bookingPins[which];
        if (!pin) {
            const el = makeMarkerEl(color, 16, false);
            const marker = new maplibregl.Marker({ element: el, draggable: true })
                .setLngLat([lng, lat])
                .addTo(this.map);
            marker.on('dragend', () => {
                const pos = marker.getLngLat();
                this._setBookingPin(which, pos.lat, pos.lng);
            });
            pin = { marker, lat, lng, label };
            this.bookingPins[which] = pin;
        } else {
            pin.marker.setLngLat([lng, lat]);
            pin.lat = lat;
            pin.lng = lng;
            pin.label = label;
        }
        this.map.flyTo({ center: [lng, lat], zoom: Math.max(this.map.getZoom(), 13), duration: 500 });
        this._dispatchSelectionChange();
    }

    getBookingSelection() {
        return {
            pickup: this.bookingPins.pickup ? { lat: this.bookingPins.pickup.lat, lng: this.bookingPins.pickup.lng, label: this.bookingPins.pickup.label } : null,
            dropoff: this.bookingPins.dropoff ? { lat: this.bookingPins.dropoff.lat, lng: this.bookingPins.dropoff.lng, label: this.bookingPins.dropoff.label } : null,
        };
    }

    clearBookingPins() {
        this.bookingPins.pickup?.marker.remove();
        this.bookingPins.dropoff?.marker.remove();
        this.bookingPins = { pickup: null, dropoff: null };
        this._dispatchSelectionChange();
    }

    _dispatchSelectionChange() {
        this.dispatchEvent(new CustomEvent('booking-selection-changed', { detail: this.getBookingSelection() }));
    }

    // ---------------- Tracking mode (live rides) ----------------

    _onStoreChange() {
        this._renderCityDots();
        if (store.selectedRideId !== this.lastRenderedRideId) this._rebuildSelected();
    }

    _onSelectionChange() {
        this._rebuildSelected();
        this._renderCityDots();
    }

    _onPosition(rideId) {
        this._renderCityDots(); // keep the non-selected dots' positions current too
        if (rideId === store.selectedRideId) this._animateSelectedDriver(rideId);
    }

    _rebuildSelected() {
        this.selectedMarkers.pickup?.remove();
        this.selectedMarkers.dropoff?.remove();
        this.selectedMarkers.driver?.remove();
        this.selectedMarkers = { pickup: null, dropoff: null, driver: null };
        this.lastRenderedRideId = store.selectedRideId;

        const ride = store.getSelected();
        if (!ride || !ride.pickup || !ride.dropoff) return;

        // A real ride is now selected - clear any leftover booking pins so
        // they don't visually clash with this ride's actual pickup/dropoff.
        this.clearBookingPins();

        this.selectedMarkers.pickup = new maplibregl.Marker({ element: makeMarkerEl('#3FB8AF', 14, false) })
            .setLngLat([ride.pickup.lng, ride.pickup.lat]).addTo(this.map);
        this.selectedMarkers.dropoff = new maplibregl.Marker({ element: makeMarkerEl('#E8604C', 14, false) })
            .setLngLat([ride.dropoff.lng, ride.dropoff.lat]).addTo(this.map);

        if (ride.driverPos) {
            this.selectedMarkers.driver = new maplibregl.Marker({
                element: makeMarkerEl('#F2A340', 20, true),
                rotationAlignment: 'map',
            }).setLngLat([ride.driverPos.lng, ride.driverPos.lat]).addTo(this.map);
        }

        this.trails.set(ride.id, ride.driverPos ? [[ride.driverPos.lng, ride.driverPos.lat]] : []);
        this._updateTrailLayer(ride.id);

        const bounds = new maplibregl.LngLatBounds()
            .extend([ride.pickup.lng, ride.pickup.lat])
            .extend([ride.dropoff.lng, ride.dropoff.lat]);
        this.map.fitBounds(bounds, { padding: 80, maxZoom: 14, duration: 600 });
    }

    _animateSelectedDriver(rideId) {
        const ride = store.rides.get(rideId);
        if (!ride?.driverPos) return;

        if (!this.selectedMarkers.driver) {
            this.selectedMarkers.driver = new maplibregl.Marker({
                element: makeMarkerEl('#F2A340', 20, true),
                rotationAlignment: 'map',
            }).setLngLat([ride.driverPos.lng, ride.driverPos.lat]).addTo(this.map);
        }

        const marker = this.selectedMarkers.driver;
        const from = marker.getLngLat();
        const to = { lng: ride.driverPos.lng, lat: ride.driverPos.lat };
        const bearing = computeBearing(from, to);
        marker.setRotation(bearing);

        // Cancel any in-flight animation for this ride before starting a new
        // one - if two ticks land close together, we don't want two competing
        // requestAnimationFrame loops fighting over the marker's position.
        const token = (this.animTokens.get(rideId) ?? 0) + 1;
        this.animTokens.set(rideId, token);

        const durationMs = 3500;
        const start = performance.now();
        const animate = (now) => {
            if (this.animTokens.get(rideId) !== token) return; // superseded by a newer tick
            const t = Math.min((now - start) / durationMs, 1);
            const lng = from.lng + (to.lng - from.lng) * t;
            const lat = from.lat + (to.lat - from.lat) * t;
            marker.setLngLat([lng, lat]); // real geographic coords every frame -
                                            // MapLibre reprojects correctly even mid-zoom/pan
            if (t < 1) requestAnimationFrame(animate);
        };
        requestAnimationFrame(animate);

        const trail = this.trails.get(rideId) ?? [];
        trail.push([to.lng, to.lat]);
        if (trail.length > 20) trail.shift();
        this.trails.set(rideId, trail);
        this._updateTrailLayer(rideId);
    }

    _updateTrailLayer(rideId) {
        if (!this.map.getSource('driver-trail')) return;
        const isSelected = rideId === store.selectedRideId;
        const coords = isSelected ? (this.trails.get(rideId) ?? []) : [];
        this.map.getSource('driver-trail').setData({
            type: 'FeatureCollection',
            features: coords.length >= 2 ? [{ type: 'Feature', geometry: { type: 'LineString', coordinates: coords } }] : [],
        });
    }

    /** Small dots for every active ride that ISN'T the currently selected one - the "citywide" view. */
    _renderCityDots() {
        const rides = store.allRidesNewestFirst();
        const activeIds = new Set(rides.filter((r) => r.id !== store.selectedRideId && !r.failed && r.status !== 'RIDE_COMPLETED').map((r) => r.id));

        for (const [rideId, marker] of this.cityDots) {
            if (!activeIds.has(rideId)) {
                marker.remove();
                this.cityDots.delete(rideId);
            }
        }

        for (const ride of rides) {
            if (!activeIds.has(ride.id)) continue;
            const pos = ride.driverPos ?? ride.pickup;
            if (!pos) continue;

            let marker = this.cityDots.get(ride.id);
            if (!marker) {
                const el = makeMarkerEl('#9CA0B8', 10, false);
                el.style.cursor = 'pointer';
                el.addEventListener('click', (evt) => {
                    evt.stopPropagation();
                    store.select(ride.id);
                });
                marker = new maplibregl.Marker({ element: el }).setLngLat([pos.lng, pos.lat]).addTo(this.map);
                this.cityDots.set(ride.id, marker);
            } else {
                marker.setLngLat([pos.lng, pos.lat]);
            }
        }
    }
}

function makeMarkerEl(color, size, isDriver) {
    const el = document.createElement('div');
    el.style.width = `${size}px`;
    el.style.height = `${size}px`;
    el.style.borderRadius = isDriver ? '3px' : '50%'; // slightly squared = "vehicle", round = "point"
    el.style.background = color;
    el.style.border = '2px solid rgba(255,255,255,0.9)';
    el.style.boxShadow = `0 0 0 5px ${color}33`;
    return el;
}

function computeBearing(from, to) {
    const toRad = (d) => (d * Math.PI) / 180;
    const toDeg = (r) => (r * 180) / Math.PI;
    const lat1 = toRad(from.lat), lat2 = toRad(to.lat);
    const dLng = toRad(to.lng - from.lng);
    const y = Math.sin(dLng) * Math.cos(lat2);
    const x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLng);
    return (toDeg(Math.atan2(y, x)) + 360) % 360;
}

export const mapController = new MapController();

export async function initMapRenderer(config) {
    await mapController.init(config);
}

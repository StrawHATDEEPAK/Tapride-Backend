// Copy this file to config.js and fill in your own values.
// config.js is gitignored - config.example.js (this file) is the only one committed.
export const CONFIG = {
    MAP_STYLE_URL: 'https://tiles.openfreemap.org/styles/dark',

    API: {
        ORDER: 'http://localhost:8081',
        PAYMENT: 'http://localhost:8082',
        MATCHING: 'http://localhost:8083',
        NOTIFICATION_WS: 'http://localhost:8084/ws',
    },

    MAP: {
        centerLat: 22.7196, // Indore, MP - matches the backend's seeded driver fleet
        centerLng: 75.8577,
        zoom: 12,
    },

    // Quick-fill buttons on the booking form - all within the seeded driver
    // fleet's radius (see matching-service's DriverSeeder).
    PRESETS: [
        { label: 'Airport → Downtown', pickup: { lat: 22.7216, lng: 75.8011 }, dropoff: { lat: 22.7196, lng: 75.8577 } },
        { label: 'Downtown → Rajwada', pickup: { lat: 22.7196, lng: 75.8577 }, dropoff: { lat: 22.7180, lng: 75.8560 } },
        { label: 'Vijay Nagar → Airport', pickup: { lat: 22.7530, lng: 75.8937 }, dropoff: { lat: 22.7216, lng: 75.8011 } },
    ],
};

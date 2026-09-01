// Copy this file to config.js. No API keys needed anywhere in this config -
// OpenFreeMap (tiles) and Nominatim (search) are both free and keyless.
export const CONFIG = {
    // OpenFreeMap - no token, no signup, no card, unlimited use, sponsored by
    // Cloudflare. Other styles available: liberty, positron, bright, fiord -
    // see https://openfreemap.org
    MAP_STYLE_URL: 'https://tiles.openfreemap.org/styles/dark',

    API: {
        ORDER: 'http://localhost:8081',
        PAYMENT: 'http://localhost:8082',
        MATCHING: 'http://localhost:8083',
        NOTIFICATION_WS: 'http://localhost:8084/ws',
    },

    // Fallback center/zoom ONLY used if the browser denies/lacks geolocation -
    // otherwise the map centers on the user's real location automatically.
    MAP: {
        centerLat: 22.7196, // Indore, MP - matches the backend's seeded driver fleet
        centerLng: 75.8577,
        zoom: 12,
    },
};

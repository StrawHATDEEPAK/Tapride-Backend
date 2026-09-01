// Used ONLY when building the frontend Docker image for the k8s deployment
// (see k8s/README.md). The docker-compose setup uses config.example.js with
// absolute http://localhost:PORT URLs, since each service is reachable on
// its own port on your machine - but in the cluster, everything sits behind
// ONE Ingress host, so the frontend calls that single host under different
// path prefixes, which the Ingress then routes to the right backend Service
// (see k8s/base/ingress.yaml).
//
// "tapride.local" isn't a real domain - it's a placeholder you map to your
// kind cluster's Ingress port in your OS hosts file (see k8s/README.md for
// the exact command). SockJS (used for the WebSocket connection) needs a
// full URL with a scheme, not a bare relative path like "/ws" - that's why
// this uses the full host rather than just path prefixes.
//
// To use: copy this file to frontend/js/config.js, THEN build/push the
// frontend image, THEN apply the k8s manifests.
export const CONFIG = {
    MAP_STYLE_URL: 'https://tiles.openfreemap.org/styles/dark',

    API: {
        ORDER: 'http://tapride.local/order-api',
        PAYMENT: 'http://tapride.local/payment-api',
        MATCHING: 'http://tapride.local/matching-api',
        NOTIFICATION_WS: 'http://tapride.local/ws',
    },

    MAP: {
        centerLat: 22.7196,
        centerLng: 75.8577,
        zoom: 12,
    },
};

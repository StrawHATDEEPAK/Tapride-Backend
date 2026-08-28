/**
 * Thin wrapper around SockJS/Stomp (loaded as plain globals via CDN in
 * index.html - they're UMD bundles, not ES modules, so we reference the
 * window globals here rather than importing them). This is the ONLY file
 * that knows those libraries exist; everything else just calls connect().
 */
export function connectWebSocket(wsUrl, onEvent, onStatusChange) {
    const sock = new SockJS(wsUrl);
    const stomp = Stomp.over(sock);
    stomp.debug = null; // silence verbose per-frame STOMP logging

    stomp.connect(
        {},
        () => {
            onStatusChange('connected');
            stomp.subscribe('/topic/events', (message) => {
                onEvent(JSON.parse(message.body));
            });
        },
        () => {
            onStatusChange('disconnected');
            // Simple reconnect-after-delay - fine for a demo; a production
            // client would want exponential backoff.
            setTimeout(() => connectWebSocket(wsUrl, onEvent, onStatusChange), 3000);
        }
    );

    return stomp;
}

import { store } from './state.js';
import { connectWebSocket } from './websocket-client.js';
import { initBookingForm } from './booking-form.js';
import { initFeedRenderer } from './render-feed.js';
import { initDetailRenderer } from './render-detail.js';
import { initMapRenderer } from './render-map.js';
import { initChaosPanel } from './chaos-panel.js';

async function main() {
    let CONFIG;
    try {
        ({ CONFIG } = await import('./config.js'));
    } catch {
        document.body.innerHTML = `
            <div style="padding: 48px; font-family: 'IBM Plex Mono', monospace; color: #EDEBE4; background: #14182B; min-height: 100vh;">
                <h2 style="color: #E8604C; margin-bottom: 16px;">Missing config.js</h2>
                <p>Copy <code style="color:#F2A340;">frontend/js/config.example.js</code> to
                   <code style="color:#F2A340;">frontend/js/config.js</code> and fill in your own
                   free Mapbox token (get one at mapbox.com).</p>
            </div>`;
        return;
    }

    initMapRenderer(CONFIG);
    initFeedRenderer();
    initDetailRenderer();
    initChaosPanel(CONFIG);
    initBookingForm(CONFIG);

    connectWebSocket(
        CONFIG.API.NOTIFICATION_WS,
        (event) => store.applyEvent(event),
        (status) => console.log('[websocket]', status)
    );
}

main();

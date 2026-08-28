import { store } from './state.js';

/**
 * The saga's real stages, in real order - this is what makes the ladder
 * meaningful rather than decorative numbering. Each stop's "done" event and
 * (where applicable) "fail" event come straight from order-service's own
 * RideEventType enum - see order-service/src/.../domain/RideEventType.java.
 */
const STOPS = [
    { label: 'Requested', done: 'RIDE_REQUESTED' },
    { label: 'Validated', done: 'RIDE_VALIDATED', fail: 'RIDE_VALIDATION_FAILED' },
    { label: 'Payment authorized', done: 'PAYMENT_AUTHORIZED', fail: 'PAYMENT_FAILED' },
    { label: 'Driver matched', done: 'DRIVER_MATCHED', fail: 'DRIVER_MATCH_FAILED' },
    { label: 'Trip in progress', done: 'RIDE_STARTED' },
    { label: 'Completed', done: 'RIDE_COMPLETED' },
];

function stopState(ride, stop) {
    if (stop.fail && ride.seen.has(stop.fail)) return 'failed';
    if (ride.seen.has(stop.done)) return 'done';
    return 'pending';
}

function formatTime(iso) {
    try {
        return new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    } catch {
        return '';
    }
}

export function initDetailRenderer() {
    const container = document.getElementById('ride-detail');

    function render() {
        const ride = store.getSelected();
        if (!ride) {
            container.innerHTML = '<p class="empty-state">Select a ride from the list to see its saga unfold here.</p>';
            return;
        }

        const states = STOPS.map((stop) => stopState(ride, stop));
        let activeIndex = -1;
        if (!ride.failed) {
            activeIndex = states.findIndex((s) => s === 'pending');
        }

        const ladderHtml = STOPS.map((stop, i) => {
            let cls;
            if (i === activeIndex) cls = 'is-active';
            else if (states[i] === 'done') cls = 'is-done';
            else if (states[i] === 'failed') cls = 'is-failed';
            else cls = 'is-pending';

            const eventRecord = ride.history.find((h) => h.eventType === stop.done || h.eventType === stop.fail);

            return `
                <div class="ladder-stop ${cls}">
                    <div class="ladder-dot"></div>
                    <div>
                        <div class="ladder-label">${stop.label}${cls === 'is-failed' ? ' — failed' : ''}</div>
                        ${eventRecord ? `<div class="ladder-time">${formatTime(eventRecord.occurredAt)}</div>` : ''}
                    </div>
                </div>
            `;
        }).join('');

        container.innerHTML = `
            <div class="card mono" style="font-size: var(--text-xs); margin-bottom: var(--space-4);">
                <div class="text-dim">Ride ID</div>
                <div>${ride.id}</div>
                ${ride.driverId ? `<div class="text-dim" style="margin-top: var(--space-2);">Driver ID</div><div>${ride.driverId}</div>` : ''}
                ${ride.failed ? `<div class="text-dim" style="margin-top: var(--space-2);">Reason</div><div style="color: var(--color-coral);">${ride.failReason}</div>` : ''}
            </div>
            <div class="status-ladder">${ladderHtml}</div>
        `;
    }

    store.addEventListener('change', render);
    store.addEventListener('select', render);
    render();
}

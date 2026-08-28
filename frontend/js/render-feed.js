import { store } from './state.js';

function statusBadge(ride) {
    if (ride.failed) return `<span class="badge badge--failed">${humanize(ride.status)}</span>`;
    if (ride.status === 'RIDE_COMPLETED') return `<span class="badge badge--done">Completed</span>`;
    return `<span class="badge badge--active">${humanize(ride.status)}</span>`;
}

function humanize(eventType) {
    // RIDE_REQUESTED -> "Requested", PAYMENT_AUTHORIZED -> "Payment authorized"
    const words = eventType.toLowerCase().split('_');
    return words.map((w, i) => i === 0 ? w[0].toUpperCase() + w.slice(1) : w).join(' ');
}

export function initFeedRenderer() {
    const listEl = document.getElementById('ride-list');
    const countEl = document.getElementById('ride-count');

    function render() {
        const rides = store.allRidesNewestFirst();
        countEl.textContent = rides.length;

        if (rides.length === 0) {
            listEl.innerHTML = '<p class="empty-state">No rides yet — book one above to see it appear here live.</p>';
            return;
        }

        listEl.innerHTML = rides.map((ride) => `
            <div class="ride-item ${ride.id === store.selectedRideId ? 'is-selected' : ''}" data-ride-id="${ride.id}">
                <div class="ride-item-top">
                    <span class="ride-item-id">#${ride.id.slice(0, 8)}</span>
                    ${statusBadge(ride)}
                </div>
                <div class="text-faint" style="font-size: var(--text-xs);">
                    ${ride.fare != null ? `Fare: $${Number(ride.fare).toFixed(2)}` : 'Estimating fare…'}
                </div>
            </div>
        `).join('');

        listEl.querySelectorAll('.ride-item').forEach((el) => {
            el.addEventListener('click', () => store.select(el.dataset.rideId));
        });
    }

    store.addEventListener('change', render);
    store.addEventListener('select', render);
    render();
}

import { bookRide } from './api.js';

// One riderId per browser session - persisted so refreshing doesn't create a
// "new person" every time, but each browser tab/visitor still gets a distinct
// identity for the demo.
function getOrCreateRiderId() {
    let id = localStorage.getItem('tapride-rider-id');
    if (!id) {
        id = crypto.randomUUID();
        localStorage.setItem('tapride-rider-id', id);
    }
    return id;
}

export function initBookingForm(config) {
    const form = document.getElementById('booking-form');
    const presetRow = document.getElementById('preset-row');
    const riderId = getOrCreateRiderId();

    const fields = {
        pickupLat: document.getElementById('pickup-lat'),
        pickupLng: document.getElementById('pickup-lng'),
        dropoffLat: document.getElementById('dropoff-lat'),
        dropoffLng: document.getElementById('dropoff-lng'),
    };

    config.PRESETS.forEach((preset) => {
        const chip = document.createElement('button');
        chip.type = 'button';
        chip.className = 'preset-chip';
        chip.textContent = preset.label;
        chip.addEventListener('click', () => {
            fields.pickupLat.value = preset.pickup.lat;
            fields.pickupLng.value = preset.pickup.lng;
            fields.dropoffLat.value = preset.dropoff.lat;
            fields.dropoffLng.value = preset.dropoff.lng;
            [...presetRow.children].forEach((c) => c.classList.remove('is-selected'));
            chip.classList.add('is-selected');
        });
        presetRow.appendChild(chip);
    });

    // Default to the first preset so the form isn't empty on load.
    if (config.PRESETS.length > 0) presetRow.children[0].click();

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        const submitBtn = form.querySelector('button[type="submit"]');
        submitBtn.disabled = true;
        submitBtn.textContent = 'Booking…';

        try {
            await bookRide(config, {
                riderId,
                pickup: { lat: parseFloat(fields.pickupLat.value), lng: parseFloat(fields.pickupLng.value) },
                dropoff: { lat: parseFloat(fields.dropoffLat.value), lng: parseFloat(fields.dropoffLng.value) },
            });
            // No need to do anything else here - the ride will appear in the
            // feed the moment order-service's RIDE_REQUESTED event arrives
            // over the WebSocket, which is typically faster than this
            // function even finishes running.
        } catch (err) {
            alert('Booking failed: ' + err.message);
        } finally {
            submitBtn.disabled = false;
            submitBtn.textContent = 'Book ride';
        }
    });
}

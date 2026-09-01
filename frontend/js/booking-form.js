import { bookRide } from './api.js';
import { searchPlaces } from './geocoding.js';
import { mapController } from './render-map.js';

function getOrCreateRiderId() {
    let id = localStorage.getItem('tapride-rider-id');
    if (!id) {
        id = crypto.randomUUID();
        localStorage.setItem('tapride-rider-id', id);
    }
    return id;
}

function setupSearchField(which, inputEl, dropdownEl) {
    inputEl.addEventListener('input', () => {
        searchPlaces(inputEl.value, (results) => {
            if (results.length === 0) {
                dropdownEl.classList.remove('is-open');
                dropdownEl.innerHTML = '';
                return;
            }
            dropdownEl.innerHTML = results.map((r, i) => `
                <div class="search-result" data-index="${i}">${r.label}</div>
            `).join('');
            dropdownEl.classList.add('is-open');

            dropdownEl.querySelectorAll('.search-result').forEach((el, i) => {
                el.addEventListener('click', () => {
                    const r = results[i];
                    inputEl.value = r.label;
                    dropdownEl.classList.remove('is-open');
                    mapController.setPointFromSearch(which, r);
                });
            });
        });
    });

    // Close the dropdown on outside click, not just on selection.
    document.addEventListener('click', (e) => {
        if (!dropdownEl.contains(e.target) && e.target !== inputEl) {
            dropdownEl.classList.remove('is-open');
        }
    });
}

export function initBookingForm(config) {
    const form = document.getElementById('booking-form');
    const riderId = getOrCreateRiderId();

    const pickupInput = document.getElementById('pickup-search');
    const dropoffInput = document.getElementById('dropoff-search');
    const pickupDropdown = document.getElementById('pickup-dropdown');
    const dropoffDropdown = document.getElementById('dropoff-dropdown');
    const pickupPickBtn = document.getElementById('pickup-pick-btn');
    const dropoffPickBtn = document.getElementById('dropoff-pick-btn');
    const submitBtn = form.querySelector('button[type="submit"]');

    setupSearchField('pickup', pickupInput, pickupDropdown);
    setupSearchField('dropoff', dropoffInput, dropoffDropdown);

    pickupPickBtn.addEventListener('click', () => {
        mapController.enterPickMode('pickup');
        pickupPickBtn.textContent = 'Click the map…';
    });
    dropoffPickBtn.addEventListener('click', () => {
        mapController.enterPickMode('dropoff');
        dropoffPickBtn.textContent = 'Click the map…';
    });

    // Keep the search inputs and the submit button in sync with whatever the
    // map currently has selected - this is the single source of truth
    // (mapController), the form just reflects it.
    mapController.addEventListener('booking-selection-changed', (e) => {
        const { pickup, dropoff } = e.detail;
        pickupInput.value = pickup?.label ?? '';
        dropoffInput.value = dropoff?.label ?? '';
        pickupPickBtn.textContent = 'Pick on map';
        dropoffPickBtn.textContent = 'Pick on map';
        submitBtn.disabled = !(pickup && dropoff);
    });
    submitBtn.disabled = true; // nothing selected yet on load

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        const { pickup, dropoff } = mapController.getBookingSelection();
        if (!pickup || !dropoff) return;

        submitBtn.disabled = true;
        submitBtn.textContent = 'Booking…';
        try {
            await bookRide(config, {
                riderId,
                pickup: { lat: pickup.lat, lng: pickup.lng },
                dropoff: { lat: dropoff.lat, lng: dropoff.lng },
            });
            pickupInput.value = '';
            dropoffInput.value = '';
            mapController.clearBookingPins();
        } catch (err) {
            alert('Booking failed: ' + err.message);
        } finally {
            submitBtn.textContent = 'Book ride';
            submitBtn.disabled = true; // re-disabled until a new pickup/dropoff is chosen
        }
    });
}

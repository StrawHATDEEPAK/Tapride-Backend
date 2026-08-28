import { setChaos, getChaos } from './api.js';

const TOGGLES = [
    {
        key: 'payment',
        name: 'Force payment failures',
        desc: 'Every ride cancels after PAYMENT_FAILED',
    },
    {
        key: 'matching',
        name: 'Force matching failures',
        desc: 'Payment succeeds, then refunds & cancels',
    },
];

export function initChaosPanel(config) {
    const container = document.getElementById('chaos-panel');
    const baseUrls = { payment: config.API.PAYMENT, matching: config.API.MATCHING };

    container.innerHTML = TOGGLES.map((t) => `
        <div class="chaos-toggle">
            <div class="chaos-toggle-label">
                <span class="chaos-toggle-name">${t.name}</span>
                <span class="chaos-toggle-desc">${t.desc}</span>
            </div>
            <div class="switch" data-key="${t.key}"></div>
        </div>
    `).join('');

    container.querySelectorAll('.switch').forEach((switchEl) => {
        const key = switchEl.dataset.key;
        const baseUrl = baseUrls[key];

        // Reflect actual current server-side state on load, rather than
        // assuming "off" - someone could have left chaos on from a previous
        // session and the UI should be honest about that.
        getChaos(baseUrl).then((state) => {
            switchEl.classList.toggle('is-on', state.forceFailure === true);
        }).catch(() => {});

        switchEl.addEventListener('click', async () => {
            const turningOn = !switchEl.classList.contains('is-on');
            switchEl.classList.toggle('is-on', turningOn); // optimistic update
            try {
                await setChaos(baseUrl, { forceFailure: turningOn });
            } catch (err) {
                switchEl.classList.toggle('is-on', !turningOn); // revert on failure
                alert(`Failed to toggle chaos: ${err.message}`);
            }
        });
    });
}

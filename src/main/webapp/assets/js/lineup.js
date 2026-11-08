'use strict';
(() => {
    for (const row of document.querySelectorAll('[data-row-link]')) {
        const navigate = () => window.location.assign(row.dataset.rowLink);
        row.addEventListener('click', (event) => {
            if (event.target.closest('button, input, select') || window.getSelection().toString()) return;
            navigate();
        });
        row.addEventListener('keydown', (event) => {
            if (event.key === 'Enter') {
                event.preventDefault();
                navigate();
            }
        });
    }
    const form = document.getElementById('lineup-form');
    const counts = document.getElementById('lineup-counts');
    if (!form || !counts) return;
    const roles = Array.from(form.querySelectorAll('[data-lineup-role]'));
    const update = () => {
        let starting = 0;
        let substitutes = 0;
        let foreign = 0;
        for (const role of roles) {
            if (role.value === 'STARTING') starting++;
            if (role.value === 'SUBSTITUTE') substitutes++;
            if (role.value && role.dataset.foreign === 'true') foreign++;
        }
        counts.textContent = `Đã chọn: ${starting} đá chính · ${substitutes} dự bị · ${foreign} ngoại binh (toàn đội hình)`;
    };
    for (const role of roles) role.addEventListener('change', update);
    update();
})();

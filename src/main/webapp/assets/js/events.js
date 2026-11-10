document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('event-form');
    if (!form) {
        return;
    }
    const side = document.getElementById('matchClubId');
    const type = document.getElementById('eventType');
    const primary = document.getElementById('primaryPlayerId');
    const related = document.getElementById('secondaryPlayerId');
    const label = document.getElementById('related-label');
    const update = () => {
        for (const option of primary.options) {
            if (!option.value) {
                continue;
            }
            const available = option.dataset.side === side.value;
            option.hidden = !available;
            option.disabled = !available;
            if (!available && option.selected) {
                primary.value = '';
            }
        }
        const hasRelated = ['NORMAL', 'FOUL', 'SUBSTITUTION'].includes(type.value);
        related.disabled = !hasRelated;
        related.required = ['FOUL', 'SUBSTITUTION'].includes(type.value);
        label.textContent = type.value === 'FOUL' ? 'Cầu thủ bị phạm lỗi' : type.value === 'SUBSTITUTION' ? 'Cầu thủ' +
            ' vào sân' : 'Cầu thủ kiến tạo (không bắt buộc)';
        for (const option of related.options) {
            if (!option.value) {
                continue;
            }
            const sameSide = option.dataset.side === side.value;
            const available = hasRelated && option.value !== primary.value
                && (type.value === 'FOUL' ? !sameSide : sameSide)
                && (type.value !== 'SUBSTITUTION' || option.dataset.role === 'SUBSTITUTE');
            option.hidden = !available;
            option.disabled = !available;
            if (!available && option.selected) {
                related.value = '';
            }
        }
        if (!hasRelated) {
            related.value = '';
        }
    };
    side.addEventListener('change', update);
    type.addEventListener('change', update);
    primary.addEventListener('change', update);
    update();
});

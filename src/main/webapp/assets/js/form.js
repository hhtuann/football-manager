document.addEventListener("DOMContentLoaded", () => {
    const dialog = document.getElementById("result-dialog");
    if (!dialog) return;

    dialog.showModal();

    dialog.addEventListener("close", () => {
        if (dialog.dataset.clearOnClose === "true") {
            document.getElementById("player-form")?.reset();
            document.getElementById("full-name")?.focus();
        }
    }, {once: true});
});

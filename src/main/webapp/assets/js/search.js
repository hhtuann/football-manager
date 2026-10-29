document.addEventListener("DOMContentLoaded", () => {
    const resultDialog = document.getElementById("delete-result-dialog");
    resultDialog?.showModal();

    const dialog = document.getElementById("delete-player-dialog");
    if (!dialog) return;

    document.querySelectorAll(".search-results .delete-btn").forEach(button => {
        button.addEventListener("click", () => {
            document.getElementById("confirm-delete-btn").value = button.dataset.playerId;
            document.getElementById("delete-player-name").textContent = button.dataset.playerName;
            dialog.showModal();
        });
    });
});

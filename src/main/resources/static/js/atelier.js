/* Améliorations progressives : formulaires et liens restent utilisables sans ce script. */
document.addEventListener("DOMContentLoaded", () => {
    const menu = document.querySelector(".bc-workspace-menu");
    if (menu) {
        document.addEventListener("click", (event) => {
            if (!menu.contains(event.target)) menu.open = false;
        });
        menu.addEventListener("keydown", (event) => {
            if (event.key === "Escape") {
                menu.open = false;
                menu.querySelector("summary").focus();
            }
        });
    }
    document.querySelectorAll("[data-prestation-id]").forEach((button) => {
        button.addEventListener("click", () => {
            const select = document.getElementById("prestationId");
            if (select) select.value = button.dataset.prestationId;
        });
    });
    document.querySelectorAll("[data-photo-upload]").forEach((form) => {
        const input = form.querySelector('input[type="file"]');
        const preview = form.querySelector("[data-photo-preview]");
        const status = form.querySelector("[data-photo-status]");
        const button = form.querySelector('button[type="submit"]');
        let previewUrl;
        input.addEventListener("change", () => {
            if (previewUrl) URL.revokeObjectURL(previewUrl);
            preview.hidden = true;
            const file = input.files[0];
            input.setCustomValidity("");
            status.textContent = "";
            if (!file) return;
            if (
                !["image/jpeg", "image/png"].includes(file.type) ||
                file.size > 5 * 1024 * 1024
            ) {
                const message =
                    "Choisissez une image JPEG ou PNG de 5 Mo maximum.";
                input.setCustomValidity(message);
                status.textContent = message;
                input.reportValidity();
                return;
            }
            previewUrl = URL.createObjectURL(file);
            preview.src = previewUrl;
            preview.hidden = false;
            status.textContent =
                "Aperçu prêt. Enregistrez pour publier la photo.";
        });
        form.addEventListener("submit", () => {
            if (form.checkValidity()) {
                button.disabled = true;
                button.textContent = "Enregistrement en cours…";
                status.textContent = "Votre photo est en cours d’envoi.";
            }
        });
    });
});

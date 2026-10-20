/* Améliorations progressives : formulaires et liens restent utilisables sans ce script. */
document.addEventListener("DOMContentLoaded", () => {
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

// Création d'une prestation : couverture et réalisations dans le même formulaire.
document.querySelectorAll('[data-service-photos]').forEach(form => {
    const main = form.querySelector('[data-service-main-photo]');
    const secondary = form.querySelector('[data-service-secondary-photos]');
    const status = form.querySelector('[data-service-photo-status]');
    let urls = [];
    function refresh() {
        urls.forEach(url => URL.revokeObjectURL(url));
        urls = [];
        main.setCustomValidity('');
        secondary.setCustomValidity('');
        const extra = Array.from(secondary.files);
        const all = [...main.files, ...extra];
        let error = '';
        if (extra.length > 4) error = 'Sélectionnez au maximum quatre photos secondaires.';
        else if (extra.length && !main.files.length) error = 'Ajoutez d’abord la photo principale.';
        else if (all.some(file => !['image/jpeg', 'image/png'].includes(file.type) || file.size > 5 * 1024 * 1024)) error = 'Chaque image doit être un JPEG ou PNG de 5 Mo maximum.';
        secondary.setCustomValidity(error);
        status.textContent = error || (all.length ? `${all.length}/5 photos prêtes à être enregistrées avec la prestation.` : '');
        for (const [files, selector] of [[Array.from(main.files), '[data-service-main-preview]'], [extra.slice(0, 4), '[data-service-secondary-previews]']]) {
            const container = form.querySelector(selector);
            container.replaceChildren();
            files.filter(file => ['image/jpeg', 'image/png'].includes(file.type) && file.size <= 5 * 1024 * 1024).forEach(file => {
                const img = document.createElement('img');
                img.src = URL.createObjectURL(file);
                urls.push(img.src);
                img.alt = `Aperçu : ${file.name}`;
                container.appendChild(img);
            });
        }
    }
    main.addEventListener('change', refresh);
    secondary.addEventListener('change', refresh);
});

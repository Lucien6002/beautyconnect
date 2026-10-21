// BeautyConnect - scripts front minimalistes.
// Ferme automatiquement les alertes Bootstrap apres quelques secondes.
document.addEventListener("DOMContentLoaded", function () {
    var alerts = document.querySelectorAll(".alert-dismissible");
    alerts.forEach(function (alertEl) {
        setTimeout(function () {
            var bsAlert = window.bootstrap
                ? new window.bootstrap.Alert(alertEl)
                : null;
            if (bsAlert) {
                bsAlert.close();
            }
        }, 6000);
    });
});

// Garder un retour explicite vers les derniers filtres, dans cet onglet seulement.
document.addEventListener("DOMContentLoaded", () => {
    document.querySelectorAll(".bc-navbar .nav-link").forEach((link) => {
        if (link.pathname === location.pathname)
            link.setAttribute("aria-current", "page");
    });
    const searchKey = "beautyconnect:last-search";
    const rememberSearch = () => {
        try {
            sessionStorage.setItem(
                searchKey,
                location.pathname + location.search,
            );
        } catch (_) {
            /* Le retour standard reste disponible. */
        }
    };
    if (location.pathname === "/recherche") {
        document.addEventListener("click", (event) => {
            if (event.target.closest("[data-profile-link]")) rememberSearch();
        });
    }
    document.querySelectorAll("[data-back-to-search]").forEach((link) => {
        try {
            const previous = sessionStorage.getItem(searchKey);
            if (!previous) return;
            const url = new URL(previous, location.origin);
            if (url.origin === location.origin && url.pathname === "/recherche")
                link.href = url.pathname + url.search;
        } catch (_) {
            /* Repli vers /recherche. */
        }
    });
    // Une prestation découverte sur l’accueil est présélectionnée dans le formulaire.
    const selected = new URLSearchParams(location.search).get("prestation");
    const services = document.getElementById("prestationId");
    if (
        selected &&
        services &&
        [...services.options].some((option) => option.value === selected)
    )
        services.value = selected;
});

// Confirmation accessible dans l’application, sans boîte de dialogue du navigateur.
document.addEventListener("DOMContentLoaded", () => {
    const dialog = document.createElement("dialog");
    dialog.className = "bc-confirm-dialog";
    dialog.setAttribute("aria-labelledby", "bc-confirm-title");
    dialog.setAttribute("aria-describedby", "bc-confirm-message");
    dialog.innerHTML = '<h2 id="bc-confirm-title">Confirmer cette action</h2><p id="bc-confirm-message"></p><div class="d-flex gap-2 justify-content-end"><button type="button" class="btn btn-outline-secondary" data-cancel autofocus>Annuler</button><button type="button" class="btn btn-danger" data-accept>Confirmer</button></div>';
    document.body.append(dialog);
    let pending = null;
    let submitter = null;
    dialog.querySelector("[data-cancel]").addEventListener("click", () => dialog.close());
    dialog.addEventListener("close", () => { pending = null; submitter = null; });
    dialog.querySelector("[data-accept]").addEventListener("click", () => {
        const form = pending;
        const button = submitter;
        pending = null;
        dialog.close();
        if (form) {
            form.dataset.confirmed = "true";
            try { if (button) form.requestSubmit(button); else form.requestSubmit(); }
            finally { delete form.dataset.confirmed; }
        }
    });
    document.querySelectorAll("form[data-confirm]").forEach(form => {
        form.addEventListener("submit", event => {
            if (form.dataset.confirmed) return;
            event.preventDefault();
            pending = form;
            submitter = event.submitter;
            dialog.querySelector("#bc-confirm-message").textContent = form.dataset.confirm;
            dialog.showModal();
        });
    });
});

// Chaque champ conserve sa valeur ; seul son affichage change dans le navigateur.
document.addEventListener("DOMContentLoaded", () => {
    const fields = [...document.querySelectorAll('input[type="password"]')];
    const masks = [];
    fields.forEach((input, index) => {
        const label = input.labels?.[0]?.textContent.trim()
            || (input.previousElementSibling?.matches("label") ? input.previousElementSibling.textContent.trim() : "mot de passe");
        if (!input.id) {
            let id = `bc-password-${index}`;
            while (document.getElementById(id)) id += "-field";
            input.id = id;
        }
        const feedback = input.nextElementSibling?.classList.contains("invalid-feedback") ? input.nextElementSibling : null;
        const wrapper = document.createElement("div");
        wrapper.className = "bc-password-field";
        input.before(wrapper);
        wrapper.append(input);
        const toggle = document.createElement("button");
        toggle.type = "button";
        toggle.className = "bc-password-toggle";
        toggle.setAttribute("aria-controls", input.id);
        const render = (visible) => {
            input.type = visible ? "text" : "password";
            toggle.setAttribute("aria-pressed", String(visible));
            const action = `${visible ? "Masquer" : "Afficher"} la saisie : ${label}`;
            toggle.setAttribute("aria-label", action);
            toggle.title = action;
            toggle.innerHTML = '<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z"/><circle cx="12" cy="12" r="3"/>' + (visible ? '<path d="m3 3 18 18"/>' : '') + '</svg>';
        };
        render(false);
        wrapper.append(toggle);
        // Garder le message de validation à côté du champ pour les styles Bootstrap.
        if (feedback) wrapper.append(feedback);
        toggle.addEventListener("click", () => {
            const start = input.selectionStart;
            const end = input.selectionEnd;
            render(input.type === "password");
            if (start !== null && end !== null) input.setSelectionRange(start, end);
        });
        const mask = () => render(false);
        masks.push(mask);
        input.form?.addEventListener("submit", mask);
    });
    window.addEventListener("pageshow", () => masks.forEach(mask => mask()));
    document.addEventListener("visibilitychange", () => {
        if (document.hidden) masks.forEach(mask => mask());
    });
});

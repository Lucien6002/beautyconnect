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

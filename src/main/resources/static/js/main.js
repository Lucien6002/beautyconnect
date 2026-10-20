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

// BeautyConnect - scripts front minimalistes.
// Ferme automatiquement les alertes Bootstrap apres quelques secondes.
document.addEventListener("DOMContentLoaded", function () {
    var alerts = document.querySelectorAll(".alert-dismissible");
    alerts.forEach(function (alertEl) {
        setTimeout(function () {
            var bsAlert = window.bootstrap ? new window.bootstrap.Alert(alertEl) : null;
            if (bsAlert) {
                bsAlert.close();
            }
        }, 6000);
    });
});

(() => {
    "use strict";
    window.beautyGeolocation = {
        locate(success, failure) {
            if (!window.isSecureContext) {
                failure("La localisation nécessite HTTPS ou localhost. Choisissez un point sur la carte.");
                return;
            }
            if (!navigator.geolocation) {
                failure("La localisation n’est pas disponible dans ce navigateur. Utilisez la ville ou la carte.");
                return;
            }
            navigator.geolocation.getCurrentPosition(success, error => {
                const messages = {
                    1: "Localisation refusée. Autorisez-la dans les paramètres du site et de votre appareil, puis réessayez.",
                    2: "Votre appareil ne fournit pas de position. Activez sa localisation ou choisissez un point sur la carte.",
                    3: "La recherche de position a expiré. Réessayez ou choisissez un point sur la carte."
                };
                failure(messages[error.code] || "Position indisponible. Utilisez la ville ou la carte.");
            }, {enableHighAccuracy: true, maximumAge: 0, timeout: 20000});
        },
        accuracy(position) {
            const metres = position.coords.accuracy;
            return Number.isFinite(metres)
                ? `Précision annoncée par votre appareil : environ ${Math.max(1, Math.round(metres))} m.`
                : "Votre appareil n’indique pas la précision de cette position.";
        }
    };
})();

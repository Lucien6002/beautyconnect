(() => {
    "use strict";
    const container = document.getElementById("location-map");
    if (!container) return;
    const status = document.getElementById("location-status");
    const gps = document.getElementById("location-gps");
    const clear = document.getElementById("location-clear");
    const latitude = document.getElementById("latitude");
    const longitude = document.getElementById("longitude");
    const valid = (lat, lon) => Number.isFinite(lat) && Number.isFinite(lon)
        && Math.abs(lat) <= 90 && Math.abs(lon) <= 180;
    const map = window.L ? L.map(container, {scrollWheelZoom: false}).setView([46.6, 1.9], 5) : null;
    if (map) {
        L.tileLayer(container.dataset.tileUrl, {
            attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
            maxZoom: 19
        }).on("tileerror", () => {
            status.textContent = "Fond de carte indisponible. La localisation et la saisie restent disponibles.";
        }).addTo(map);
    } else {
        container.hidden = true;
        status.textContent = "Carte indisponible. Utilisez votre position ou saisissez les coordonnées.";
    }
    let marker = null;
    function select(lat, lon, focusMap = false) {
        if (!valid(lat, lon)) return;
        latitude.value = lat.toFixed(6);
        longitude.value = lon.toFixed(6);
        if (map && !marker) {
            marker = L.marker([lat, lon], {
                draggable: true,
                title: "Point public sélectionné",
                alt: "Point public sélectionné",
            }).addTo(map);
            marker.on("dragend", () => {
                const position = marker.getLatLng();
                select(position.lat, position.lng);
            });
        } else if (marker) marker.setLatLng([lat, lon]);
        if (focusMap && map) map.setView([lat, lon], 15);
        status.textContent =
            "Point sélectionné. Vérifiez la position et votre accord de publication, puis enregistrez.";
    }
    function fromInputs(focusMap) {
        if (!latitude.value.trim() || !longitude.value.trim()) return;
        select(Number(latitude.value), Number(longitude.value), focusMap);
    }
    fromInputs(true);
    map?.on("click", (event) => select(event.latlng.lat, event.latlng.lng));
    latitude.addEventListener("change", () => fromInputs(true));
    longitude.addEventListener("change", () => fromInputs(true));
    gps.addEventListener("click", () => {
        gps.disabled = true;
        status.textContent = "Recherche de votre position…";
        window.beautyGeolocation.locate(position => {
            select(position.coords.latitude, position.coords.longitude, true);
            status.textContent += " " + window.beautyGeolocation.accuracy(position)
                + " Corrigez le point si nécessaire avant d’enregistrer.";
            gps.disabled = false;
        }, message => {
            gps.disabled = false;
            status.textContent = message;
        });
    });
    clear.addEventListener("click", () => {
        if (marker) {
            map.removeLayer(marker);
            marker = null;
        }
        latitude.value = "";
        longitude.value = "";
        document.getElementById("coordinatesPublic").checked = false;
        status.textContent =
            "Point retiré du formulaire. Enregistrez pour appliquer ce changement.";
    });
})();

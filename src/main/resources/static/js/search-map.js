(() => {
    'use strict';
    const config = window.beautyMap;
    const status = document.getElementById('map-status');
    const validPosition = (lat, lon) => typeof lat === 'number' && typeof lon === 'number'
        && Number.isFinite(lat) && Number.isFinite(lon) && Math.abs(lat) <= 90 && Math.abs(lon) <= 180;
    const hasOrigin = validPosition(config.latitude, config.longitude);
    const gps = document.getElementById('btn-autour-de-moi');
    gps.addEventListener('click', () => {
        if (!navigator.geolocation) { status.textContent = 'GPS indisponible : utilisez le filtre ville.'; return; }
        gps.disabled = true;
        status.textContent = 'Recherche de votre position…';
        navigator.geolocation.getCurrentPosition(position => {
            document.getElementById('clientLatitude').value = position.coords.latitude;
            document.getElementById('clientLongitude').value = position.coords.longitude;
            gps.closest('form').requestSubmit();
        }, () => {
            gps.disabled = false;
            status.textContent = 'Position indisponible ou autorisation refusée : utilisez le filtre ville.';
        }, {timeout: 10000, maximumAge: 60000, enableHighAccuracy: false});
    });
    document.getElementById('clear-position').addEventListener('click', event => {
        document.getElementById('clientLatitude').value = '';
        document.getElementById('clientLongitude').value = '';
        event.target.closest('form').requestSubmit();
    });
    if (!window.L) {
        status.textContent = 'La carte est indisponible. Vous pouvez utiliser la liste et les liens transports.';
        document.getElementById('map').hidden = true;
        document.querySelectorAll('.show-on-map').forEach(button => { button.hidden = true; });
        return;
    }
    const map = L.map('map').setView(hasOrigin ? [config.latitude, config.longitude] : [46.6, 1.9], hasOrigin ? 12 : 5);
    L.tileLayer(config.tileUrl, {
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
        maxZoom: 19
    }).on('tileerror', () => { status.textContent = 'Le fond de carte est indisponible. La liste reste utilisable.'; }).addTo(map);
    const markers = new Map();
    const points = [];
    let routeLayer = null;
    let routeRequest = null;
    if (hasOrigin) {
        L.circleMarker([config.latitude, config.longitude], {radius: 8}).addTo(map).bindPopup('Vous êtes ici');
        points.push([config.latitude, config.longitude]);
    }
    const directionsUrl = (pro, mode) => {
        const params = new URLSearchParams({api: '1', destination: `${pro.latitude},${pro.longitude}`, travelmode: mode});
        if (hasOrigin) params.set('origin', `${config.latitude},${config.longitude}`);
        return `https://www.google.com/maps/dir/?${params}`;
    };
    const link = (label, href) => {
        const a = document.createElement('a');
        a.textContent = label; a.href = href; a.className = 'd-block mt-2';
        a.target = '_blank'; a.rel = 'noopener noreferrer';
        return a;
    };
    const showRoute = async (pro, button) => {
        if (!hasOrigin) { status.textContent = 'Activez « Autour de moi » pour tracer un trajet.'; return; }
        if (routeRequest) routeRequest.abort();
        const request = new AbortController();
        routeRequest = request;
        const timeout = setTimeout(() => request.abort(), 10000);
        button.disabled = true;
        status.textContent = 'Calcul du trajet à pied…';
        if (routeLayer) { map.removeLayer(routeLayer); routeLayer = null; }
        try {
            const params = new URLSearchParams({lat: config.latitude, lon: config.longitude});
            const response = await fetch(`/itineraire/professionnels/${pro.id}?${params}`, {signal: request.signal});
            if (!response.ok || response.redirected) throw new Error('Route indisponible');
            const data = await response.json();
            if (!Number.isFinite(data.distanceKm) || !Number.isFinite(data.durationMinutes)) throw new Error('Route invalide');
            const geometry = typeof data.geometry === 'string' ? JSON.parse(data.geometry) : data.geometry;
            if (!geometry || geometry.type !== 'LineString' || !Array.isArray(geometry.coordinates)) throw new Error('Route invalide');
            if (routeRequest !== request) return;
            routeLayer = L.geoJSON(geometry, {style: {color: '#6244c5', weight: 5}}).addTo(map);
            map.fitBounds(routeLayer.getBounds(), {padding: [30, 30]});
            status.textContent = `Trajet à pied estimé vers ${pro.name} : ${data.distanceKm.toFixed(1)} km, environ ${Math.round(data.durationMinutes)} min.`;
        } catch (error) {
            if (routeRequest === request) status.textContent = 'Calcul piéton indisponible. Utilisez « À pied dans Google Maps » dans la bulle du professionnel.';
        } finally {
            clearTimeout(timeout);
            button.disabled = false;
            if (routeRequest === request) routeRequest = null;
        }
    };
    config.professionals.forEach(pro => {
        if (!validPosition(pro.latitude, pro.longitude)) return;
        const popup = document.createElement('div');
        const title = document.createElement('strong'); title.textContent = pro.name; popup.append(title);
        const profile = document.createElement('a');
        profile.textContent = 'Voir le profil'; profile.href = `/professionnels/${pro.id}`; profile.className = 'd-block mt-2'; popup.append(profile);
        const button = document.createElement('button');
        button.type = 'button'; button.textContent = 'Y aller à pied'; button.className = 'btn btn-sm btn-primary mt-2';
        button.addEventListener('click', () => showRoute(pro, button)); popup.append(button);
        popup.append(link('À pied dans Google Maps', directionsUrl(pro, 'walking')));
        popup.append(link('Bus / transports', directionsUrl(pro, 'transit')));
        const marker = L.marker([pro.latitude, pro.longitude]).addTo(map).bindPopup(popup);
        marker.on('click', () => {
            document.querySelectorAll('[id^="professional-"] .card').forEach(card => card.classList.remove('border-primary', 'border', 'border-2'));
            const card = document.getElementById(`professional-${pro.id}`);
            if (card) card.querySelector('.card').classList.add('border-primary', 'border', 'border-2');
        });
        markers.set(String(pro.id), marker);
        points.push([pro.latitude, pro.longitude]);
    });
    document.querySelectorAll('.show-on-map').forEach(button => {
        button.addEventListener('click', () => {
            const marker = markers.get(button.dataset.proId);
            if (!marker) return;
            map.setView(marker.getLatLng(), 14); marker.openPopup();
            document.getElementById('map').scrollIntoView({block: 'center'});
        });
    });
    if (points.length) map.fitBounds(points, {padding: [30, 30], maxZoom: 14});
    if (!markers.size) status.textContent = 'Aucun professionnel de cette page n’a de coordonnées publiables. Consultez la liste.';
})();

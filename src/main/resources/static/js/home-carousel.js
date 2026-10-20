// Les quatre images de Joyce, intégrées à la mise en page actuelle.
(() => {
    "use strict";
    const root = document.querySelector("[data-bc-carousel]");
    if (!root) return;
    const frame = root.querySelector(".bc-carousel-frame");
    let current = frame.querySelector("img");
    const sources = [
        "acceuil1.png",
        "acceuil.jpg",
        "acceuil2.jpg",
        "acceuil3.jpg",
    ].map((name) => new URL(name, current.src).href);
    const controls = root.querySelector(".bc-carousel-controls");
    const counter = root.querySelector("[data-carousel-counter]");
    const pause = root.querySelector("[data-carousel-pause]");
    const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
    const interval = Number(root.dataset.carouselInterval) || 8000;
    let index = 0;
    let paused = reducedMotion.matches;
    let timer = null;
    let loading = false;
    let hovered = false;
    let focused = false;
    let previous = null;

    function stop() {
        clearTimeout(timer);
        timer = null;
    }
    function schedule() {
        stop();
        if (!paused && !hovered && !focused && !document.hidden && !loading) {
            timer = setTimeout(() => show(index + 1), interval);
        }
    }
    function updatePause() {
        pause.setAttribute("aria-pressed", String(paused));
        pause.setAttribute(
            "aria-label",
            paused
                ? "Reprendre le défilement"
                : "Mettre en pause le défilement",
        );
        pause.querySelector("i").className = paused
            ? "bi bi-play-fill"
            : "bi bi-pause-fill";
    }
    async function show(nextIndex) {
        if (loading) return;
        stop();
        loading = true;
        const next = (nextIndex + sources.length) % sources.length;
        const image = new Image();
        image.alt = "";
        image.className = "bc-carousel-image";
        image.src = sources[next];
        try {
            // Charger l'image suivante seulement au moment nécessaire.
            await image.decode();
            if (previous) previous.remove();
            previous = current;
            const outgoing = current;
            frame.append(image);
            // Laisser le navigateur enregistrer l'état initial avant le fondu.
            requestAnimationFrame(() =>
                requestAnimationFrame(() => {
                    image.classList.add("is-current");
                    outgoing.classList.remove("is-current");
                }),
            );
            current = image;
            index = next;
            counter.textContent = `${index + 1} / ${sources.length}`;
        } catch (_) {
            // Conserver la photo affichée si une image ne peut pas être chargée.
        } finally {
            loading = false;
            schedule();
        }
    }
    controls.hidden = false;
    root.querySelector("[data-carousel-previous]").addEventListener(
        "click",
        () => show(index - 1),
    );
    root.querySelector("[data-carousel-next]").addEventListener("click", () =>
        show(index + 1),
    );
    pause.addEventListener("click", () => {
        paused = !paused;
        updatePause();
        schedule();
    });
    root.addEventListener("mouseenter", () => {
        hovered = true;
        stop();
    });
    root.addEventListener("mouseleave", () => {
        hovered = false;
        schedule();
    });
    root.addEventListener("focusin", () => {
        focused = true;
        stop();
    });
    root.addEventListener("focusout", (event) => {
        if (!root.contains(event.relatedTarget)) {
            focused = false;
            schedule();
        }
    });
    document.addEventListener("visibilitychange", schedule);
    reducedMotion.addEventListener("change", (event) => {
        if (event.matches) paused = true;
        updatePause();
        schedule();
    });
    updatePause();
    schedule();
})();

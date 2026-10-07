# Ulrich — client, rendez-vous, recherche facile et carte

## État du lot au 6 octobre 2026

Le code local comprend les transitions RDV, le verrouillage des réservations, l’occupation distincte de l’historique, l’activation configurable avec renvoi, la recherche paginée, les coordonnées publiables sur accord du professionnel, Leaflet, la validation GPS, ORS Matrix/Directions avec repli et les liens Google Maps. Docker, les profils dev/prod, les migrations Flyway et le workflow CI sont préparés. Voir [déploiement et recette](../DEPLOIEMENT-ULRICH.md) et [passation aux autres lots](ULRICH-PASSATION.md).

Les étapes U1–U12 ci-dessous décrivent les exigences du lot ; leur présence dans le code ne remplace pas la recette. U13 reste externe : accès Render, choix du plan par l’équipe, clés et SMTP, fusion des autres lots, sauvegarde/restauration et recette publique. Le système visuel final attend les tokens livrés par Joyce ; les pages utilisent les composants communs existants.

> Parcours recommandé : lire [l’audit](../AUDIT-TECHNIQUE.md), puis [le guide commun Git/design/Render](../ROADMAP-EQUIPE.md), puis suivre les étapes ci-dessous. Les fonctions nouvelles sont proposées ; vérifier le code avant de les implémenter.


**Objectif :** un client trouve les pros les plus proches, obtient un chemin à pied calculé et réserve un vrai créneau ; tu prends aussi en charge le déploiement Render. **Branche :** `feature/ulrich-client-carte`. **Charge :** 14 à 16 jours, difficulté 5/5. Lire [`AUDIT-TECHNIQUE.md`](../AUDIT-TECHNIQUE.md), surtout sections 3–6 et 9, puis [le guide commun](../ROADMAP-EQUIPE.md). Ton récapitulatif antérieur (activation, Mailpit, données démo) correspond en partie aux fichiers présents ; **les tests du parcours client sont maintenant dans `ClientJourneyTests`, avec le routage dans `RoutingClientTests` et les migrations dans `PostgresMigrationTests`.**

## Parcours du code à maîtriser

1. Inscription : `AuthController.registerClient` → `UserService.registerClient` → `AuthTokenService.generateAndSendActivationLink` → `UserRepository`/`VerificationTokenRepository` → `templates/auth/register-client.html`.
2. Réservation : `templates/professional/profile.html` → `AppointmentController.book` → `AppointmentService.book` → `TimeSlotRepository`, `PrestationRepository`, `AppointmentRepository` → `templates/client/appointments.html`.
3. Confirmation : `ProfessionalDashboardController.confirmAppointment` → `AppointmentService.confirm` → `EmailService.sendAppointmentConfirmation`.
4. Recherche : `HomeController.home` → `SearchController.search` → `ProfessionalService.search` → `ProfessionalProfileRepository.search` → `templates/search/results.html`.
5. La configuration `application.properties` est localement modifiée ; ne pas l'inclure dans ta PR sans coordination avec Emmanuelle. Mailpit dans `docker-compose.yml` intercepte les mails de développement, mais `AuthTokenService` pointe encore vers `localhost`.

## Tâches par petites PR

| Étape | Durée | Action et preuve de fin |
|---|---:|---|
| U1 | 1 j | Écrire avec Manuel un tableau autorisé `EN_ATTENTE→CONFIRME/REFUSE/ANNULE`, `CONFIRME→TERMINE/ANNULE` selon décision équipe ; bloquer les autres transitions dans `AppointmentService`, tests de chaque refus. |
| U2 | 1,5 j | Sécuriser `AppointmentService.book` : pro validé/actif, prestation active du même pro, créneau futur, un seul gagnant concurrent, notes bornées ; test H2 de réservation double et ID trafiqués. Se coordonner avec Manuel sur `TimeSlot`. |
| U3 | 1 j | Stabiliser le mail : `APP_BASE_URL` configurable pour activation, expiration issue d'une vraie propriété, service mail simulé en test, inscription sans compte « coincé » si SMTP indisponible ; convenir avec Emmanuelle des variables prod. |
| U4 | 1 j | Améliorer `templates/client/dashboard.html` et `appointments.html` selon les tokens de Joyce : état, date, pro, annulation autorisée, message vide, lien itinéraire. Vérifier `LAZY` via test MVC réel. |
| U5 | 1 j | Rendre la recherche facile : champ de nom, ville, type, clientèle, prix éventuel selon décision ; filtres conservés, résultats paginés et lisibles. Écrire le contrat avec Anaelle avant de changer `ProfessionalProfileRepository.search`. |
| U6 | 1,5 j | Ajouter une carte Leaflet accessible dans `templates/search/results.html`, avec marqueurs des seuls pros validés et géolocalisés, synchronisation liste/carte, liens fiche, état sans coordonnées, affichage mobile. Données de coordonnées fournies par Anaelle + migration Emmanuelle ; ne pas inventer de points. |
| U7 | 1 j | Avec consentement navigateur, recevoir la position ponctuelle ; côté serveur, calcul Haversine pour présélectionner les pros validés et compatibles. Sans consentement, garder la ville. Ne pas stocker la position du client dans `User` ni dans les logs. |
| U8 | 1,5 j | Créer `RoutingClient` et l’implémentation openrouteservice côté serveur. API Matrix sur les candidats : distance réelle et durée à pied, tri « les plus proches à pied », limite/précision annoncées. Clé `ORS_API_KEY` dans l’environnement ; faux client en test ; quota/timeout/cache/repli. |
| U9 | 1,5 j | API Directions pour un seul pro choisi : tracer le chemin à pied dans Leaflet, afficher distance/durée, erreurs et absence de route. Tester le mode `shortest` avant d’utiliser le mot « plus court ». Aucun appel direct à ORS depuis le navigateur. |
| U10 | 0,5 j | Bouton « Bus / transports » vers Google Maps Directions (`api=1`, `destination` encodée, `travelmode=transit`) et bouton de repli piéton. Les lignes et horaires sont fournis par Google Maps. |
| U11 | 1,5 j | Tests de parcours : inscription/activation, recherche, réservation/confirmation/refus/annulation, mail simulé, carte avec/sans coordonnées, faux `RoutingClient`, ordre des proches et URL transports. Démo Sarah/Léa seulement en profil dev. |
| U12 | 1–1,5 j | Préparer `Dockerfile` Java 21 multi-étape et `render.yaml` si Blueprint choisi ; brancher `PORT`, `APP_BASE_URL`, URL JDBC PostgreSQL et variables préparées par Emmanuelle. Tester le conteneur localement avec PostgreSQL, sans secret dans Git. |
| U13 | 1 j | Après fusion des lots et accès Render : connecter le dépôt à un service Web Docker et une base PostgreSQL, configurer les variables, déployer `main`, puis smoke tester accueil, recherche/carte, activation, RDV, admin et images avec Emmanuelle. Documenter rollback/redéploiement. Le plan et tout coût doivent être validés par l'équipe. |

**Dans le code local :** les transitions sont contrôlées ; les avis de recherche sont agrégés en une requête ; latitude/longitude et accord de publication sont présents ; les appels Matrix et Directions restent côté serveur. Le classement piéton porte sur les candidats mesurés de la page. Sans clé ORS ou si le fournisseur échoue, la liste reste utilisable avec des distances à vol d’oiseau et un repli Google Maps.

**Contrat de la recherche géographique à implémenter (implémenté localement) :**

| Entrée ou sortie | Comportement attendu |
|---|---|
| `GET /recherche` | Conserver les filtres actuels (`SearchCriteria.city`, `type`, `gender`) ; ajouter nom et option « autour de moi ». L'origine GPS n'est utilisée qu'après accord explicite du navigateur et doit être validée (`latitude` −90..90, `longitude` −180..180). |
| Candidats | `ProfessionalProfileRepository` retourne les pros validés, actifs et compatibles, avec coordonnées publiables. Présélectionner un nombre borné par distance Haversine ; garder les résultats sans coordonnées dans la liste, avec « distance indisponible ». |
| Calcul réel | `NearestProfessionalService` appelle `RoutingClient.matrixWalking` côté serveur sur les candidats bornés ; trier par durée piétonne si l'étiquette dit « plus rapide à pied », ou par distance du chemin si elle dit « plus proche à pied ». Montrer km et minutes, estimation seulement ; traiter les routes impossibles et les quotas. |
| `GET /itineraire/professionnels/{id}` | Réponse JSON minimale de géométrie/distance/durée depuis une origine validée ; pro public seulement. `RoutingClient.directionsWalking` appelle le fournisseur côté serveur. Ne jamais renvoyer la clé API. Limiter la fréquence et la taille de la réponse. |
| Interface | Liste et carte restent synchronisées ; un clic sur une carte affiche la fiche, le trajet piéton sur la carte, un bouton « Bus / transports » externe et un lien de secours si le calcul échoue. L'origine GPS n'est pas conservée en base. |

**Exemple à tester :** pro A à 800 m à vol d'oiseau mais à 2,4 km à pied (rivière), pro B à 1,2 km à vol d'oiseau mais à 1,5 km à pied. Le service doit placer B avant A après la matrice. Si A n'entre pas dans la présélection, l'interface doit dire que le classement porte sur les candidats mesurés ; ne pas prétendre à un classement global parfait. Le bus reste un lien Google Maps : intégrer lignes et horaires dans BeautyConnect demanderait une API de transport dédiée et un autre lot.

**Fichiers en écriture :** `controller/AuthController.java`, `AppointmentController.java`, `ClientDashboardController.java`, `SearchController.java`; `service/UserService.java`, `AuthTokenService.java`, `AppointmentService.java`, nouveaux `RoutingClient.java` et `NearestProfessionalService.java`; `repository/AppointmentRepository.java`, `ProfessionalProfileRepository.java` en accord avec Anaelle; `dto/AppointmentForm.java`, `SearchCriteria.java`; `templates/client/*`, `templates/search/results.html`; nouveau JS carte ; `Dockerfile`, `render.yaml` si choisi et guide de déploiement ; tests correspondants. **Lecture ou modification convenue :** `ProfessionalDashboardController.java` (Manuel), `ProfessionalService.java`/`ProfessionalProfile.java` et fiche pro (Anaelle), `custom.css` (Joyce), configuration/migrations (Emmanuelle), avis (Teddy).

## Ce que tu dois apprendre

Transactions et concurrence JPA (`@Transactional`, verrouillage/version, contrainte unique) ; contrôles de propriétaire et de rôle ; `@Valid`/`BindingResult` ; sécurité par session/CSRF ; séparation des données privées et publiques ; Leaflet, coordonnées GPS, géocodage, liens d'itinéraire ; tests H2 avec service mail simulé ; Docker, Render, PostgreSQL, variables d'environnement et diagnostic de démarrage. Explique pourquoi un simple `if (slot.isAvailable())` ne suffit pas pour deux clients simultanés.

## Critères de terminé

- Deux réservations concurrentes pour le même créneau : une seule réussit et l'autre reçoit une erreur métier claire.
- Aucune réservation de prestation désactivée, pro non validé ou créneau passé ; annulation/confirmation répétée refusée.
- Recherche facile sur mobile, liste utilisable sans JS, carte des seuls points publiables, classement des candidats mesurés par trajet à pied, chemin tracé et lien transports correctement encodé ; aucun secret côté navigateur.
- Activation utilise la vraie URL publique sur Render ; tests sans SMTP ni réseau externe ; PR lisible avec captures et explication des règles.
- `main` déployé sur Render après CI verte et recette ; aucun compte démo ni secret dans l'image ; procédure de redéploiement et rollback comprise par l'équipe.

## Prompt à donner à ton IA

> Tu accompagnes Ulrich sur BeautyConnect. Lis ce fichier, `docs/AUDIT-TECHNIQUE.md` et les fichiers réels cités dans « Parcours du code ». Commence par me faire expliquer le flux client et la différence entre `enabled` et `validated`. Pour la tâche U[NUMÉRO], montre la règle métier, les classes et méthodes impliquées, les cas limites, puis propose une modification minimale dans mes fichiers. Pour la carte, enseigne Leaflet, Haversine, matrices de distance, coordonnées privées et différence entre chemin à pied intégré et bus Google Maps externe. Pour U12/U13, enseigne Docker, Render, JDBC, variables d'environnement et rollback ; utilise la configuration et les migrations d'Emmanuelle. Implémente avec tests qui détectent une erreur réelle. Ne colle aucun secret ni URL privée dans le code ou le prompt. Termine par une mini leçon, un exemple que je peux expliquer à l'équipe, les tests lancés et les étapes Git/PR.

## Publier ton travail sur GitHub

Après publication de la documentation sur `main`, créer ta branche depuis `main`. Vérifier `git status` avant de changer de branche : la modification locale actuelle de `application.properties` appartient à Ulrich et ne doit pas être incluse par accident.

```bash
git fetch origin
git switch main
git pull --ff-only origin main
git switch -c feature/ulrich-client-carte
# coder une tâche, puis vérifier et sélectionner seulement ses modifications (`git add -p` pour les fichiers suivis ; `git add chemin/du/nouveau/fichier` pour un nouveau fichier)
git status --short
git add -p
git commit -m "feat(ulrich): decrire la tache"
git push -u origin feature/ulrich-client-carte
```

Ouvrir une Pull Request vers `main`, avec tâche numérotée, règles métier, captures si UI, tests exécutés, ce que tu as appris et fichiers partagés touchés. Demander une revue à un autre membre ; fusionner seulement après CI verte. Si `main` avance, `git fetch origin` puis `git merge origin/main` dans ta branche et refaire les tests. Pour le cycle complet et Render, voir [le guide commun](../ROADMAP-EQUIPE.md).

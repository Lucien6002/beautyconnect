# BeautyConnect — plan de travail pour six personnes

**Point de départ :** audit vérifié dans [AUDIT-TECHNIQUE.md](AUDIT-TECHNIQUE.md). L'état réel est celui de la branche locale `feature/ulrich-activation-rdv-audit` au 28/09/2026. `src/main/resources/application.properties` comporte déjà une modification locale non commitée ; personne ne doit l'écraser. Les évolutions ci-dessous sont des **tâches proposées**, pas des fonctionnalités existantes.

## 1. Propriétaires et livrables

| Personne | Branche proposée | Zone principale | Livrable |
|---|---|---|---|
| [Joyce](roadmaps/JOYCE.md) | `feature/joyce-design-system` | CSS, fragments, accueil et auth | Identité visuelle et base responsive |
| [Ulrich](roadmaps/ULRICH.md) | `feature/ulrich-client-carte` | Compte, réservation, carte, itinéraire et Render | Parcours client et déploiement |
| [Anaelle](roadmaps/ANAELLE.md) | `feature/anaelle-catalogue-profil` | Profil, prestations, données de localisation | Vitrine et CRUD complet |
| [Manuel](roadmaps/MANUEL.md) | `feature/manuel-agenda-pro` | Créneaux et RDV côté pro | Agenda et gestion des demandes |
| [Teddy](roadmaps/TEDDY.md) | `feature/teddy-admin-moderation` | Admin, avis et signalements | Modération fiable |
| [Emmanuelle](roadmaps/EMMANUELLE.md) | `feature/emmanuelle-qualite-media` | CI, migrations, images, tests d'intégration et sauvegardes | Socle qualité et médias |

L'attribution d'Emmanuelle est une **proposition** : sa tâche n'était pas renseignée dans les notes fournies. Durée indicative : **deux itérations de cinq jours**, ajustée après la première CI verte. Le design et la revue UI se font pendant les autres lots. Chaque tâche a une taille visée de **0,5 à 2 jours**.

## 2. Ordre et contrats entre les branches

| Moment | Accord à obtenir | Responsable |
|---|---|---|
| Jour 1 | Figé : noms des variables CSS, composants, espacements, cartes d'état | Joyce, puis tous |
| Jour 1 | Figé : états de RDV autorisés, condition de visibilité pro, `enabled` vs email vérifié | Ulrich, Anaelle, Manuel, Teddy |
| Jour 1–2 | Extraction des routes `/pro/creneaux` et `/pro/rendez-vous` de `ProfessionalDashboardController` sans changer les URL | Manuel, petite PR prioritaire |
| Jour 2 | Figé : schéma `latitude/longitude` et gestion des adresses privées avant migration | Ulrich + Anaelle + Emmanuelle |
| Jour 2 | Figé : contrat `ImageStorageService` (`upload`, `delete`, URL, `publicId`) | Anaelle + Emmanuelle |
| Jour 3 | Validation du contrat « un avis par rendez-vous terminé » | Ulrich + Teddy |
| Fin itération 1 | Démo de bout en bout locale sur H2/PostgreSQL et revue de chaque PR | Tous |
| Fin itération 2 | Audit final et smoke test Render ; aucun identifiant de démonstration en production | Ulrich (déploiement) + Emmanuelle (recette) + tous |

Les changements transverses `build.gradle`, `SecurityConfig.java`, `application.properties`, `User.java`, `ProfessionalProfile.java`, `Appointment.java` et `ProfessionalDashboardController.java` se font dans une PR courte et annoncée avant les autres. **Une personne à la fois** modifie chacun de ces fichiers. Les propriétaires de templates métier appliquent les tokens de Joyce sans modifier `custom.css`.

## 3. Direction visuelle : un site utilisable et identifiable

**Public :** étudiants et nouveaux arrivants qui veulent choisir vite une prestation locale fiable. **Tâche principale de l'accueil :** chercher par prestation, ville et budget, puis comprendre les disponibilités. La signature visuelle proposée est un **carnet de quartier** : cartes de professionnels avec photo réelle, prestation d'entrée, prix, créneau et quartier visibles sans ouvrir chaque fiche. Aucune métrique ou photo fictive présentée comme réelle.

| Token proposé | Valeur | Usage |
|---|---|---|
| `--bc-ink` | `#281F35` | titres, navigation, texte fort |
| `--bc-text` | `#3E354A` | corps |
| `--bc-muted` | `#665D70` | aide, métadonnées |
| `--bc-primary` | `#6244C5` | action principale et focus |
| `--bc-primary-soft` | `#EEE9FC` | filtres actifs, surfaces |
| `--bc-coral` | `#D85F69` | accent rare, jamais seul indicateur d'erreur |
| `--bc-mint` | `#D9F2E9` | confirmation/disponibilité |
| `--bc-canvas` | `#FAF9FC` | fond |
| `--bc-surface` | `#FFFFFF` | cartes et formulaires |

Typographie proposée : **Fraunces** pour titres courts, **Manrope** pour lecture/formulaires ; police système en repli. Échelle : `clamp(2.25rem, 5vw, 4rem)` pour le héros, `2rem` pour H1 interne, `1.25rem` pour H2, `1rem` pour le corps, `0.875rem` pour métadonnées. Espacements par pas de `0.25rem`, rayon 12–16 px, ombre très discrète, focus visible et contraste vérifié. Sur mobile : cartes en une colonne, filtres repliables, CTA de réservation visible. Animations courtes seulement si elles servent l'action ; respecter `prefers-reduced-motion`.

Inspiration de **fonction**, sans copier leur habillage : [Planity](https://www.planity.com/) pour la recherche et l'accès à la réservation, [Treatwell](https://www.treatwell.fr/) pour comparer les prestations, [Fresha](https://www.fresha.com/blog/fresha-service-portfolio-feature) pour l'idée d'un portfolio relié à une prestation. Chaque propriétaire transforme ces idées en composants originaux BeautyConnect.

## 4. Carte et images : décisions techniques proposées

### Carte et itinéraires — responsabilité d'Ulrich

- **MVP** : [Leaflet](https://leafletjs.com/examples/quick-start/) dans `search/results.html`, marqueurs seulement pour les profils validés qui ont des coordonnées ; liste HTML toujours utilisable sans JavaScript. Ulrich possède `SearchController`, `ProfessionalProfileRepository.search` et `search/results.html` pour cette étape. Anaelle fournit les coordonnées et l'accord de publication du pro ; Emmanuelle fournit la migration. Ne montrer que la ville ou un point approximatif si l'adresse privée ne doit pas être publiée.
- Aujourd'hui `ProfessionalProfile` n'a que `city`/`address` (`model/ProfessionalProfile.java`) : ajouter `latitude`, `longitude`, statut/date de géocodage avec migration. Ne jamais inventer de coordonnées. Aucun appel de géocodage à chaque affichage de page.
- **Les plus proches** : avec consentement GPS, présélection à vol d'oiseau côté serveur, puis comparaison des distances et durées piétonnes des candidats par [openrouteservice Matrix](https://giscience.github.io/openrouteservice/v8.2.0/api-reference/endpoints/matrix/). Afficher « les plus proches parmi les résultats mesurés » ; sans GPS ou fournisseur, garder les filtres ville/type/nom. Clé `ORS_API_KEY` côté serveur, jamais dans JavaScript.
- **Chemin sur notre carte** : appeler [openrouteservice Directions](https://giscience.github.io/openrouteservice/api-reference/endpoints/directions/) pour un pro sélectionné ; tracer la géométrie piétonne dans Leaflet, afficher distance/durée. Le mot « plus court » n'est utilisé que si le mode fournisseur `shortest` est validé par test ; sinon « trajet à pied estimé ». Limiter les appels, gérer quota, timeout et cache.
- **Bus / transports** : ouvrir [Google Maps Directions via URL](https://developers.google.com/maps/documentation/urls/get-started) avec `destination` encodée et `travelmode=transit`. Google Maps affiche les lignes/horaires disponibles. BeautyConnect ne calcule pas lui-même les transports dans ce lot.
- Pour une petite démo, un géocodage **à la sauvegarde**, avec cache, peut utiliser Nominatim public seulement après accord explicite de l'équipe sur son usage et sa politique : maximum 1 requête/s pour toute l'application, User-Agent identifiant, attribution, sans autocomplétion ni batch périodique ([politique officielle](https://operations.osmfoundation.org/policies/nominatim/)). Une saisie manuelle de ville/coordonnées pour la démo est le repli le plus simple.
- Les tuiles OpenStreetMap publiques exigent attribution, référent valide, cache et absence de préchargement ; disponibilité sans garantie. Prévoir une URL de fournisseur configurable pour la production ([politique officielle](https://operations.osmfoundation.org/policies/tiles/)).

### Images hébergées

- **Candidat recommandé pour le MVP** : [Cloudinary Free](https://cloudinary.com/pricing), sous réserve de vérifier quota/conditions au moment de créer le compte. Leur [API d'upload](https://cloudinary.com/documentation/image_upload_api_reference) et [SDK Java](https://cloudinary.com/documentation/java_integration) permettent upload, récupération par `secure_url` et suppression par `public_id` ; le secret API doit rester côté serveur. Alternative : [Supabase Storage Free](https://supabase.com/pricing), quota gratuit plus restreint et projet gratuit sujet à pause d'inactivité.
- Flux : formulaire pro `multipart/form-data` → contrôleur avec `@AuthenticationPrincipal` → service vérifie propriétaire, taille (5 Mo max déjà dans `application.properties`), vrai type MIME et dimensions → `ImageStorageService` → Cloudinary → stocker en PostgreSQL `publicId`, `secureUrl`, propriétaire, ordre et éventuellement prestation liée → afficher URL HTTPS via Thymeleaf. À suppression/remplacement : supprimer le fichier distant et sa ligne, avec traitement d'erreur explicite. `profilePhotoUrl` actuel seul ne suffit pas pour gérer la suppression.
- Variables d'environnement à créer par le propriétaire du compte : `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`. **Aucun mot de passe, URL signée, secret ou fichier `.env` sur GitHub**. L'API key seule ne remplace pas le secret ; ne jamais mettre le secret dans JS, HTML, URL ou prompt partagé.
- Les comptes et clés externes ne sont pas fournis par le code : leur création est **À VÉRIFIER** par l'équipe. Le code doit pouvoir être testé avec un faux `ImageStorageService` sans accès réseau.
- **Relire vs restaurer :** une image active est relue via `secureUrl`. Si l'équipe veut récupérer une image effacée, activer le [backup automatique Cloudinary](https://cloudinary.com/documentation/backups_and_version_management) **avant** les uploads ; il est désactivé par défaut, disponible sur les plans selon leur documentation, et consomme du quota. Tester une restauration depuis la bibliothèque/API avec un fichier de démonstration. Sans backup activé avant suppression, la récupération n'est pas garantie.

## 5. Workflow Git partagé

**Publier la documentation** : l'utilisateur a demandé une publication directe sur `main`. Ajouter uniquement `docs/` depuis une copie isolée de `main`, puis pousser `main` après vérification ; ne jamais inclure la modification locale de `application.properties`. Pour les travaux de code suivants, chacun crée sa branche depuis le `main` actualisé. Chaque membre a besoin d'un accès GitHub accordé par le propriétaire du dépôt ; les permissions actuelles sont **NON TROUVÉ** dans le code.

```bash
git status --short
git fetch origin
git switch main
git pull --ff-only origin main
git switch -c feature/mon-sujet
```

Pendant le travail : `git status`, petit commit `feat(pro): ...`, `fix(rdv): ...`, `test(admin): ...` ou `docs: ...`, puis `git push -u origin <branche>`. Ouvrir une PR contenant objectif, fichiers, captures pour UI, tests exécutés, risques et dépendances. Une revue par un autre membre et CI verte avant fusion. Si `main` avance : `git fetch origin`, `git merge origin/main` dans la branche, résoudre les conflits ensemble, relancer les tests. **Ne pas pousser directement sur `main` en parallèle** : `main` reçoit les PR validées ; Render déploiera cette branche après la phase de recette. Branch protection GitHub (PR + CI) à activer par le propriétaire du dépôt.

## 6. Plan Render pour la fin — Ulrich propriétaire

Render recommande Docker pour une application JVM comme BeautyConnect ([documentation Render](https://render.com/docs/docker)). Préparer `Dockerfile` multi-étape Java 21, exécuter le JAR avec `server.port=${PORT:8080}`, créer une base PostgreSQL Render et passer les valeurs de connexion par variables d'environnement. La config actuelle construit une URL JDBC depuis `DB_*` : utiliser ces variables correctement ou ajouter `SPRING_DATASOURCE_URL` JDBC ; ne pas coller directement une URL `postgresql://...` dans une propriété JDBC sans adaptation. Render peut déclarer service + base dans `render.yaml` ([Blueprint spec](https://render.com/docs/blueprint-spec)). Choix du plan, coût, région et domaine : **À VÉRIFIER** avec l'équipe avant création.

Avant le premier déploiement, Ulrich récupère le socle préparé par Emmanuelle : 1) données démo limitées au profil `dev` ; 2) aucun secret ni mot de passe par défaut en prod ; 3) migrations SQL ; 4) `APP_BASE_URL` pour l'activation ; 5) transport mail par variables ; 6) variables Cloudinary si les images sont livrées ; 7) CI verte ; 8) smoke test `/`, `/recherche`, inscription/activation, réservation, admin et images avec Emmanuelle en recette ; 9) vérifier journaux et restauration des sauvegardes. Les images ne doivent pas dépendre du disque éphémère du conteneur.

## 7. Apprendre pendant chaque tâche

Avant de coder, chacun lit sa fiche nominative liée dans la section 1, suit le parcours indiqué dans l'audit, puis explique avec ses mots : « navigateur → contrôleur → service → repository → base → vue ». Après chaque tâche, il écrit dans sa PR : **ce qu'il a appris**, **quelle règle métier il a protégée**, **comment il l'a vérifiée**. L'IA peut enseigner et proposer du code, mais le développeur doit pouvoir expliquer chaque fichier modifié et faire la démonstration à un autre membre.
